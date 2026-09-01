package persistence.checkpoint;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import game.Game;
import persistence.DbConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Algoritma 2 checkpoint'lerini {@code algo2_checkpoint} tablosuna yazar.
 *
 * <ul>
 *   <li>{@code maybeRecord} → solutionIndex araligin kati ise state'i buffer'a alir.</li>
 *   <li>buffer {@code flushEvery}'ye ulasinca tek batch commit.</li>
 *   <li>{@code close} → kalan buffer flush + kaynaklar kapatilir.</li>
 *   <li>JVM shutdown hook: kalan buffer'i yazmaya calisir.</li>
 * </ul>
 *
 * Tek is parcacigi icindir. Mevcut solver_run / path_explorer_solution tablolariyla
 * iliskisi yok.
 */
public final class Algo2CheckpointWriter implements CheckpointRecorder {

    private static final String INSERT = """
            INSERT INTO algo2_checkpoint
              (run_id, solution_index, row_size, col_size, algo_version, interval_size,
               step, path_len, dir_count, path, visited_dirs, exit_situation, one_way_list,
               round_counter, round_counter_overlong, total_solved, total_solved_overlong,
               total_back_step, dummy_back_move, locked_back_lose, square_total_solved)
            VALUES (?,?,?,?,?,?, ?,?,?,?,?,?,?, ?,?,?,?, ?,?,?,?)
            ON CONFLICT (run_id, solution_index) DO NOTHING
            """;

    private final HikariDataSource dataSource;
    private final UUID runId = UUID.randomUUID();
    private final int interval;
    private final int flushEvery;

    private final List<Pending> buffer = new ArrayList<>();
    private final Thread shutdownHook;
    private boolean closed = false;

    private record Pending(long solutionIndex, Algo2Snapshot snap) { }

    public Algo2CheckpointWriter(DbConfig cfg, Algo2CheckpointConfig ccfg, int rowSize, int colSize) {
        this.interval = Math.max(1, ccfg.intervalFor(rowSize, colSize));
        this.flushEvery = Math.max(1, ccfg.flushEvery());

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.url());
        hc.setUsername(cfg.user());
        hc.setPassword(cfg.password());
        hc.setMaximumPoolSize(1);
        hc.setPoolName("algo2-checkpoint");
        this.dataSource = new HikariDataSource(hc);

        this.shutdownHook = new Thread(this::onJvmShutdown, "algo2-checkpoint-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public UUID runId() {
        return runId;
    }

    public int interval() {
        return interval;
    }

    @Override
    public void maybeRecord(Game game, long solutionIndex) {
        if (solutionIndex % interval != 0) {
            return;
        }
        buffer.add(new Pending(solutionIndex, Algo2Snapshot.capture(game)));
        if (buffer.size() >= flushEvery) {
            flush();
        }
    }

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
        } catch (SQLException e) {
            throw new IllegalStateException("algo2_checkpoint yazilamadi: " + e.getMessage(), e);
        } finally {
            buffer.clear();
        }
    }

    private void bind(PreparedStatement ps, Pending pending) throws SQLException {
        Algo2Snapshot s = pending.snap();
        int i = 1;
        ps.setObject(i++, runId);
        ps.setLong(i++, pending.solutionIndex());
        ps.setInt(i++, s.rowSize());
        ps.setInt(i++, s.colSize());
        ps.setShort(i++, Algo2Snapshot.ALGO_VERSION);
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
        ps.setBoolean(i++, s.lockedBackLose());
        ps.setInt(i, s.squareTotalSolved());
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            flush();
        } catch (RuntimeException ignored) {
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
            flush();
        } catch (RuntimeException ignored) {
        }
        dataSource.close();
    }
}
