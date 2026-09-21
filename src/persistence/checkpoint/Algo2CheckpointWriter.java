package persistence.checkpoint;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import game.Game;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import persistence.DbConfig;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cozucu checkpoint'lerini {@code solving_checkpoint} tablosuna yazar. SADECE
 * Algoritma 2 (state RoadMemory'ye bagli).
 *
 * <ul>
 *   <li>{@code maybeRecord} → solutionIndex, interval'in kati ise state'i buffer'a alir.</li>
 *   <li>buffer {@code flushEvery}'ye ulasinca tek batch commit (ON CONFLICT DO NOTHING).</li>
 *   <li>{@code close} / JVM shutdown → kalan buffer + interval'e denk gelmeyen SON snapshot yazilir.</li>
 * </ul>
 *
 * Tekillik: TAM STATE bazinda ({@code solution_index, grid_map_id, algorithm_id,
 * interval_size, step, path_len, dir_count, path, visited_dirs, exit_situation,
 * one_way_list, round_counter, total_back_steps, dummy_back_steps, locked_back_lose}
 * - bkz. {@code docker/initdb/05_solving_checkpoint_full_state_unique.sql}). Ayni
 * solution_index icin ayni state tekrar YAZILMAZ (ON CONFLICT DO NOTHING); ama
 * checkpoint'ten resume edilip ayni aralik tekrar oynatildiginda FARKLI bir state
 * uretilirse (bug/non-determinism), bu tekillige TAKILMAZ ve ayrı bir satir olarak
 * eklenir - yani anomali sessizce kaybolmaz. DB hatalari cozucuyu DURDURMAZ -
 * loglanip devam edilir.
 *
 * {@code total_solved} kolonu duruyor (resume sonrasi sayaclarin dogru surdugunu
 * gozle kontrol etmek icin) ama constraint'te DEGIL - solution_index'le zaten
 * bire-bir orantili oldugu icin ayirt edicilik katmiyor. {@code algorithm_version}
 * ve {@code square_total_solved} tamamen kaldirildi (bkz. proje notlari).
 */
public final class Algo2CheckpointWriter implements CheckpointRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(Algo2CheckpointWriter.class);

    private static final String INSERT = """
            INSERT INTO solving_checkpoint
              (solving_run_id, solution_index, grid_map_id, algorithm_id, interval_size,
               step, path_len, dir_count, path, visited_dirs, exit_situation, one_way_list,
               round_counter, round_counter_overlong, total_solved, total_solved_overlong,
               total_back_steps, dummy_back_steps, locked_back_lose)
            VALUES (?,?,?,?,?, ?,?,?,?,?,?,?, ?,?,?,?, ?,?,?)
            ON CONFLICT (solution_index, grid_map_id, algorithm_id, interval_size,
                         step, path_len, dir_count, path, visited_dirs, exit_situation, one_way_list,
                         round_counter, total_back_steps, dummy_back_steps, locked_back_lose) DO NOTHING
            RETURNING solution_index
            """;

    private final HikariDataSource dataSource;
    private final UUID solvingRunId = UUID.randomUUID();
    private final int interval;
    private final int flushEvery;
    private final int algorithmId;
    private int gridMapId = -1;
    private boolean disabled = false;

    private final List<Pending> buffer = new ArrayList<>();
    private final Thread shutdownHook;
    private boolean closed = false;

    // Her cozumun snapshot'i buraya alinir; interval'de buffer'a eklenir. Kapanista
    // interval'e denk gelmese bile SON snapshot yazilir.
    private Pending lastPending;
    private boolean lastPendingStored = false;

    private record Pending(long solutionIndex, Algo2Snapshot snap) { }

    public Algo2CheckpointWriter(DbConfig cfg, Algo2CheckpointConfig ccfg,
                                 int rowSize, int colSize, int algorithmId) {
        this.interval = Math.max(1, ccfg.intervalFor(rowSize, colSize));
        this.flushEvery = Math.max(1, ccfg.flushEvery());
        this.algorithmId = algorithmId;

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.url());
        hc.setUsername(cfg.user());
        hc.setPassword(cfg.password());
        hc.setMaximumPoolSize(1);
        hc.setPoolName("solving-checkpoint");
        this.dataSource = new HikariDataSource(hc);

        try {
            this.gridMapId = resolveGridMapId(rowSize, colSize);
        } catch (RuntimeException e) {
            disabled = true;
            logWarn("checkpoint devre disi - " + e.getMessage());
        }

        this.shutdownHook = new Thread(this::onJvmShutdown, "solving-checkpoint-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public UUID solvingRunId() {
        return solvingRunId;
    }

    public int interval() {
        return interval;
    }

    private int resolveGridMapId(int rowSize, int colSize) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM grid_map WHERE row_size = ? AND col_size = ?")) {
            ps.setInt(1, rowSize);
            ps.setInt(2, colSize);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("grid_map'te " + rowSize + "x" + colSize
                            + " yok. Once ekle: INSERT INTO grid_map (id, row_size, col_size) VALUES (<id>, "
                            + rowSize + ", " + colSize + ");");
                }
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("grid_map_id cozulemedi: " + e.getMessage(), e);
        }
    }

    @Override
    public void maybeRecord(Game game, long solutionIndex) {
        if (disabled) {
            return;
        }
        Pending pending = new Pending(solutionIndex, Algo2Snapshot.capture(game));
        lastPending = pending;
        lastPendingStored = false;
        if (solutionIndex % interval == 0) {
            buffer.add(pending);
            lastPendingStored = true;
            if (buffer.size() >= flushEvery) {
                flush();
            }
        }
    }

    /** Interval'e denk gelmediyse son cozumun snapshot'ini da yaz. */
    private void persistLastIfNeeded() {
        if (!disabled && lastPending != null && !lastPendingStored) {
            buffer.add(lastPending);
            lastPendingStored = true;
        }
    }

    /** DB hatasi cozucuyu durdurmaz: loglanir, buffer bosaltilir, devam edilir. */
    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
                for (Pending pending : buffer) {
                    bind(ps, pending);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
                try {
                    printSkippedDiagnostics(ps, buffer);
                } catch (SQLException diagEx) {
                    logWarn("atlanan (zaten var olan) satirlar okunamadi - veri kaybi yok, sadece log basilamadi: "
                            + diagEx.getMessage());
                }
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            logWarn("solving_checkpoint batch yazilamadi (" + buffer.size() + " satir atlandi): " + e.getMessage());
            printFailureDiagnostics(e, buffer);
        } finally {
            buffer.clear();
        }
    }

    /**
     * ON CONFLICT DO NOTHING nedeniyle atlanan (DB'de TAM AYNI STATE ile zaten var olan)
     * satirlarin tam verisini konsola basar - bu bir HATA DEGIL, deterministik kiyaslama
     * icindir (bkz. compare-solution-checkpoint.md paragraf 5): checkpoint'ten resume edilip
     * ayni aralik tekrar oynatildiginda uretilen state ile DB'deki satirin BIREBIR ayni olup
     * olmadigi elle kiyaslanabilsin diye.
     *
     * RETURNING solution_index sadece GERCEKTEN eklenen satirlar icin deger dondurur
     * (Postgres: DO NOTHING'e dusen satirlar RETURNING ciktisinda hic gorunmez); bu yuzden
     * getGeneratedKeys() ile donen kumede OLMAYAN her pending satir "zaten vardi, atlandi"
     * demektir - JDBC batch update-count dizisine (reWriteBatchedInserts=true altinda
     * guvenilir degil) bagli kalmadan dogru sonuc verir.
     */
    private void printSkippedDiagnostics(PreparedStatement ps, List<Pending> attempted) throws SQLException {
        Set<Long> inserted = new HashSet<>();
        try (ResultSet keys = ps.getGeneratedKeys()) {
            while (keys.next()) {
                inserted.add(keys.getLong(1));
            }
        }
        List<Pending> skipped = new ArrayList<>();
        for (Pending pending : attempted) {
            if (!inserted.contains(pending.solutionIndex())) {
                skipped.add(pending);
            }
        }
        if (skipped.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[SKIP] ").append(skipped.size())
                .append(" satir DB'de TAM AYNI STATE ile zaten vardi (ON CONFLICT DO NOTHING) - ")
                .append("deterministik kiyaslama icin tam veri:");
        int i = 1;
        for (Pending pending : skipped) {
            appendPendingDetail(sb, i++, skipped.size(), pending, "zaten var - atlandi");
        }
        LOG.info(sb.toString());
    }

    /**
     * DB'ye YAZILAMAYAN satirlarin tam verisini konsola basar - compare-solution-checkpoint.md'de
     * anlatilan "DB disinda kalan veriyi elle kiyasla" akisi icin. Sira: 1) hata mesaji (yukarida
     * {@link #logWarn} ile zaten basildi), 2) SQLException zinciri (gercek sebep - constraint adi
     * genelde burada, ozellikle BatchUpdateException.getNextException()'da), 3) her basarisiz
     * satirin TUM alanlari (solution_index, grid_map_id, algorithm_id, interval_size, step,
     * path_len, dir_count, path, visited_dirs, exit_situation, one_way_list, round_counter,
     * total_solved, total_back_steps, dummy_back_steps, locked_back_lose).
     */
    private void printFailureDiagnostics(Exception e, List<Pending> failed) {
        StringBuilder sb = new StringBuilder();
        if (e instanceof SQLException top) {
            sb.append("[ERROR] sebep zinciri (SQLState / ErrorCode - gercek Postgres nedeni genelde son halkada):");
            SQLException cur = top;
            int depth = 0;
            while (cur != null) {
                sb.append("\n  ").append(depth).append(") ").append(cur.getClass().getSimpleName())
                        .append(" SQLState=").append(cur.getSQLState())
                        .append(" ErrorCode=").append(cur.getErrorCode())
                        .append(" : ").append(cur.getMessage());
                cur = cur.getNextException();
                depth++;
            }
        } else {
            sb.append("[ERROR] sebep: ").append(e.getClass().getSimpleName()).append(" : ").append(e.getMessage());
        }

        sb.append("\n[DATA] DB'ye kaydedilemeyen ").append(failed.size())
                .append(" satirin tam verisi (DB'deki karsiligiyla elle kiyaslamak icin - bkz. compare-solution-checkpoint.md):");
        int i = 1;
        for (Pending pending : failed) {
            appendPendingDetail(sb, i++, failed.size(), pending, "kaydedilemeyen satir");
        }
        LOG.error(sb.toString(), e);
    }

    private void appendPendingDetail(StringBuilder sb, int index, int total, Pending pending, String label) {
        Algo2Snapshot s = pending.snap();
        sb.append("\n  ---- ").append(label).append(' ').append(index).append('/').append(total).append(" ----");
        sb.append("\n    solution_index   = ").append(pending.solutionIndex());
        sb.append("\n    grid_map_id      = ").append(gridMapId).append("  (").append(s.rowSize()).append('x').append(s.colSize()).append(')');
        sb.append("\n    algorithm_id     = ").append(algorithmId);
        sb.append("\n    interval_size    = ").append(interval);
        sb.append("\n    step             = ").append(s.step());
        sb.append("\n    path_len         = ").append(s.step());
        sb.append("\n    dir_count        = ").append(s.dirCount());
        sb.append("\n    path             = ").append(decodePath(s));
        sb.append("\n    visited_dirs     = ").append(toHex(s.visitedDirs())).append("  (").append(countSetBits(s.visitedDirs())).append(" bit set / ").append(s.step() * s.dirCount()).append(" toplam)");
        sb.append("\n    exit_situation   = ").append(s.exitSituation());
        sb.append("\n    one_way_list     = ").append(decodeOneWayList(s.oneWayList()));
        sb.append("\n    round_counter    = ").append(s.roundCounter()).append("  (overlong=").append(s.roundCounterOverlong()).append(')');
        sb.append("\n    total_solved     = ").append(s.totalSolved()).append("  (overlong=").append(s.totalSolvedOverlong()).append(")  [supheli - unique constraint'e DAHIL DEGIL]");
        sb.append("\n    total_back_steps = ").append(s.totalBackStep());
        sb.append("\n    dummy_back_steps = ").append(s.dummyBackMove());
        sb.append("\n    locked_back_lose = ").append(s.lockedBackLose());
    }

    /** path[k] = (k+1). adimin hucre indeksi (x*colSize+y) -> okunabilir (x,y) dizisi. */
    private static String decodePath(Algo2Snapshot s) {
        byte[] path = s.path();
        int cols = s.colSize();
        StringBuilder sb = new StringBuilder("[");
        for (int k = 0; k < s.step() && k < path.length; k++) {
            int cell = path[k] & 0xFF;
            if (k > 0) sb.append(',');
            sb.append('(').append(cell / cols).append(',').append(cell % cols).append(')');
        }
        return sb.append(']').toString();
    }

    /** packOneWayList formati: count:int, sonra her navigasyon icin step/oneWayValue/compulsoryDirId/exitLocatedHere. */
    private static String decodeOneWayList(byte[] raw) {
        ByteBuffer buf = ByteBuffer.wrap(raw);
        int count = buf.getInt();
        StringBuilder sb = new StringBuilder("count=").append(count);
        for (int i = 0; i < count; i++) {
            int step = buf.getInt();
            int oneWayValue = buf.getInt();
            int compulsoryDirId = buf.getInt();
            byte exitLocatedHere = buf.get();
            sb.append(" | [step=").append(step)
                    .append(" oneWayValue=").append(oneWayValue)
                    .append(" compulsoryDirId=").append(compulsoryDirId)
                    .append(" exitLocatedHere=").append(exitLocatedHere).append(']');
        }
        return sb.toString();
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static int countSetBits(byte[] b) {
        int n = 0;
        for (byte x : b) n += Integer.bitCount(x & 0xFF);
        return n;
    }

    private void bind(PreparedStatement ps, Pending pending) throws SQLException {
        Algo2Snapshot s = pending.snap();
        int i = 1;
        ps.setObject(i++, solvingRunId);
        ps.setLong(i++, pending.solutionIndex());
        ps.setInt(i++, gridMapId);
        ps.setInt(i++, algorithmId);
        ps.setInt(i++, interval);

        ps.setInt(i++, s.step());
        ps.setInt(i++, s.step());
        ps.setInt(i++, s.dirCount());
        ps.setBytes(i++, s.path());
        ps.setBytes(i++, s.visitedDirs());
        ps.setInt(i++, s.exitSituation());
        ps.setBytes(i++, s.oneWayList());

        ps.setLong(i++, s.roundCounter());
        ps.setInt(i++, s.roundCounterOverlong());
        ps.setLong(i++, s.totalSolved());
        ps.setInt(i++, s.totalSolvedOverlong());

        ps.setLong(i++, s.totalBackStep());
        ps.setLong(i++, s.dummyBackMove());
        ps.setBoolean(i, s.lockedBackLose());
    }

    private static void logWarn(String msg) {
        LOG.warn(msg);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            persistLastIfNeeded();
            flush();
        } catch (RuntimeException e) {
            logWarn("kapanista: " + e.getMessage());
        }
        dataSource.close();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
        }
    }

    private void onJvmShutdown() {
        if (closed || dataSource.isClosed()) {
            return;
        }
        try {
            persistLastIfNeeded();
            flush();
        } catch (RuntimeException e) {
            logWarn("shutdown: " + e.getMessage());
        }
        dataSource.close();
    }
}
