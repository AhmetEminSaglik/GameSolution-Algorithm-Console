package persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bulunan cozumleri 1000'lik (yapilandirilabilir) batch'lerle PostgreSQL'e yazar.
 *
 * <ul>
 *   <li>{@code beginRun} → {@code solver_run} satiri, {@code runId} alinir.</li>
 *   <li>{@code accept} → buffer'a; buffer dolunca {@link #flush()}.</li>
 *   <li>{@code endRun} → son {@code flush()} + {@code solver_run} COMPLETED update.</li>
 *   <li>{@code close} → kalan buffer flush + kaynaklar kapatilir.</li>
 *   <li>JVM shutdown hook: normal bitmemisse → flush + {@code solver_run} ABORTED.</li>
 * </ul>
 *
 * Tek is parcacigi (bir cozucu) icindir. Paralel calisilirsa her thread kendi
 * ornegini + kendi {@code solver_run}'ini kullanmali.
 */
public final class JdbcSolutionSink implements SolutionSink {

    private static final String INSERT_SOLUTION = """
            INSERT INTO path_explorer_solution
              (public_id, solver_run_id, solution_index, row_size, col_size, grid_size,
               start_x, start_y, path_len, path, open1, open2, open3)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private final HikariDataSource dataSource;
    private final int batchSize;
    private final List<FoundSolution> buffer = new ArrayList<>();
    private final Thread shutdownHook;

    private Connection batchConnection;
    private long runId = -1;
    private boolean runFinished = false;
    private boolean closed = false;

    public JdbcSolutionSink(DbConfig cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.url());
        hc.setUsername(cfg.user());
        hc.setPassword(cfg.password());
        hc.setMaximumPoolSize(2);
        hc.setPoolName("pathexplorer-sink");
        this.dataSource = new HikariDataSource(hc);
        this.batchSize = cfg.batchSize();

        this.shutdownHook = new Thread(this::onJvmShutdown, "pathexplorer-sink-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public void beginRun(RunInfo info) {
        String sql = """
                INSERT INTO solver_run (public_id, row_size, col_size, algorithm, status, save_mode)
                VALUES (?,?,?,?, 'RUNNING', 'flat')
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setInt(2, info.rowCount());
            ps.setInt(3, info.colCount());
            ps.setString(4, info.algorithm());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                runId = keys.getLong(1);
            }
            // Batch insert'ler icin ayri, uzun omurlu baglanti (autocommit kapali).
            batchConnection = dataSource.getConnection();
            batchConnection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new IllegalStateException("solver_run olusturulamadi: " + e.getMessage(), e);
        }
    }

    @Override
    public void accept(FoundSolution solution) {
        buffer.add(solution);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    /** Buffer'daki her seyi tek batch olarak yazar ve commit eder. */
    public void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = batchConnection.prepareStatement(INSERT_SOLUTION)) {
            for (FoundSolution fs : buffer) {
                GridPath p = fs.path();
                int gridSize = p.rowCount() * 1000 + p.colCount();
                ps.setObject(1, UUID.randomUUID());
                ps.setLong(2, runId);
                ps.setLong(3, fs.solutionIndex());
                ps.setInt(4, p.rowCount());
                ps.setInt(5, p.colCount());
                ps.setInt(6, gridSize);
                ps.setInt(7, p.startX());
                ps.setInt(8, p.startY());
                ps.setInt(9, p.length());
                ps.setBytes(10, p.encode());
                ps.setInt(11, p.cellIndexAtStep(0));
                setNullableCell(ps, 12, p, 1);
                setNullableCell(ps, 13, p, 2);
                ps.addBatch();
            }
            ps.executeBatch();
            batchConnection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("batch flush basarisiz: " + e.getMessage(), e);
        } finally {
            buffer.clear();
        }
    }

    @Override
    public void endRun(RunResult result) {
        flush();
        String sql = """
                UPDATE solver_run
                   SET total_solved = ?, round_counter = ?, total_back_steps = ?,
                       dummy_back_steps = ?, status = 'COMPLETED', finished_at = now()
                 WHERE id = ?
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, result.totalSolved());
            ps.setLong(2, result.roundCounter());
            ps.setLong(3, result.totalBackSteps());
            ps.setLong(4, result.dummyBackSteps());
            ps.setLong(5, runId);
            ps.executeUpdate();
            runFinished = true;
        } catch (SQLException e) {
            throw new IllegalStateException("solver_run guncellenemedi: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (!runFinished && !buffer.isEmpty()) {
                flush();
            }
        } catch (RuntimeException ignored) {
        }
        closeBatchConnection();
        dataSource.close();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM zaten kapaniyorsa
        }
    }

    private void onJvmShutdown() {
        if (closed || runFinished || dataSource.isClosed()) {
            return;
        }
        try {
            flush();
        } catch (RuntimeException ignored) {
        }
        if (runId > 0) {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE solver_run SET status = 'ABORTED', finished_at = now() WHERE id = ? AND status = 'RUNNING'")) {
                ps.setLong(1, runId);
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        }
        closeBatchConnection();
        dataSource.close();
    }

    private void setNullableCell(PreparedStatement ps, int idx, GridPath p, int step) throws SQLException {
        if (step < p.length()) {
            ps.setInt(idx, p.cellIndexAtStep(step));
        } else {
            ps.setNull(idx, Types.SMALLINT);
        }
    }

    private void rollbackQuietly() {
        try {
            if (batchConnection != null && !batchConnection.isClosed()) {
                batchConnection.rollback();
            }
        } catch (SQLException ignored) {
        }
    }

    private void closeBatchConnection() {
        try {
            if (batchConnection != null && !batchConnection.isClosed()) {
                batchConnection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
