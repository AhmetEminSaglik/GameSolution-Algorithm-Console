package persistence.checkpoint;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import game.Game;
import persistence.DbConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
            try (PreparedStatement ps = c.prepareStatement(INSERT)) {
                for (Pending pending : buffer) {
                    bind(ps, pending);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            logWarn("solving_checkpoint batch yazilamadi (" + buffer.size() + " satir atlandi): " + e.getMessage());
        } finally {
            buffer.clear();
        }
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
        System.err.println("[checkpoint][WARN] " + msg);
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
