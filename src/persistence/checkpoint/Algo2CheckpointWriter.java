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
 *   <li>{@code maybeRecord} → solutionIndex araligin kati ise state'i buffer'a alir.</li>
 *   <li>buffer {@code flushEvery}'ye ulasinca tek batch commit.</li>
 *   <li>{@code close} → kalan buffer flush + kaynaklar kapatilir.</li>
 *   <li>JVM shutdown hook: kalan buffer'i yazmaya calisir.</li>
 * </ul>
 *
 * {@code grid_map_id} kurulusta (row,col) → grid_map.id ile cozulur; {@code algorithm_id}
 * disaridan verilir (BaseSolution.getSolutionCreatedOrder()). Mevcut solver_run /
 * path_explorer_solution tablolariyla iliskisi yok.
 */
public final class Algo2CheckpointWriter implements CheckpointRecorder {

    private static final String INSERT = """
            INSERT INTO solving_checkpoint
              (solving_run_id, solution_index, grid_map_id, algorithm_id, algorithm_version, interval_size,
               step, path_len, dir_count, path, visited_dirs, exit_situation, one_way_list,
               round_counter, round_counter_overlong, total_solved, total_solved_overlong,
               total_back_step, dummy_back_move, locked_back_lose, square_total_solved)
            VALUES (?,?,?,?,?,?, ?,?,?,?,?,?,?, ?,?,?,?, ?,?,?,?)
            ON CONFLICT (solving_run_id, solution_index) DO NOTHING
            """;

    private final HikariDataSource dataSource;
    private final UUID solvingRunId = UUID.randomUUID();
    private final int interval;
    private final int flushEvery;
    private final int gridMapId;
    private final int algorithmId;

    private final List<Pending> buffer = new ArrayList<>();
    private final Thread shutdownHook;
    private boolean closed = false;

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

        this.gridMapId = resolveGridMapId(rowSize, colSize);

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
                            + " kaydi yok. Once ekle:  INSERT INTO grid_map (id, row_size, col_size) VALUES (<id>, "
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
            throw new IllegalStateException("solving_checkpoint yazilamadi: " + e.getMessage(), e);
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
        ps.setShort(i++, Algo2Snapshot.ALGORITHM_VERSION);
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
