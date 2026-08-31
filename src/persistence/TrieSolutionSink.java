package persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Cozumleri bir <b>onek agacinda (trie)</b> saklar: ortak onek 1 kez.
 *
 * <p>Cozucunun DFS'ini takip eder ({@code onRoot / onForward / onBackward}).
 * Aktif yol bellekte bir yigin; bir dugum, altindaki tum dallar tukenince
 * (geri adimda) DB'ye yazilir ve bellekten atilir → bellek O(derinlik).
 *
 * <p><b>Budama:</b> yalnizca en az bir tamamlanmis cozume goturen dugumler
 * saklanir. Cikmaz dallar (subtreeCount == 0) yazilmadan atilir.
 *
 * <p>Dugum id'leri client tarafinda atanir (parent id, cocuktan once lazim;
 * cocuk parent'tan once flush edilir).
 *
 * <p>Tek is parcacigi icindir.
 */
public final class TrieSolutionSink implements SolutionSink {

    private static final String INSERT_STEP = """
            INSERT INTO solution_step
              (id, run_id, grid_map_id, parent_step_id, step_no, x, y,
               move_from_parent, solution_ordinal, subtree_solution_count, is_leaf)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """;

    private final HikariDataSource dataSource;
    private final int batchSize;
    private final Thread shutdownHook;

    private final Deque<Node> pathStack = new ArrayDeque<>();
    private final List<Node> flushBuffer = new ArrayList<>();

    private Connection batchConnection;
    private long runId = -1;
    private int gridMapId = -1;
    private long nextNodeId = 1;
    private long solutionsFound = 0;
    private long nodesKept = 0;
    private boolean runFinished = false;
    private boolean closed = false;

    public TrieSolutionSink(DbConfig cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.url());
        hc.setUsername(cfg.user());
        hc.setPassword(cfg.password());
        hc.setMaximumPoolSize(2);
        hc.setPoolName("pathexplorer-trie");
        this.dataSource = new HikariDataSource(hc);
        this.batchSize = cfg.batchSize();
        this.shutdownHook = new Thread(this::onJvmShutdown, "pathexplorer-trie-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public void beginRun(RunInfo info) {
        try (Connection c = dataSource.getConnection()) {
            gridMapId = resolveGridMapId(c, info.rowCount(), info.colCount());
            String sql = """
                    INSERT INTO solver_run (public_id, row_size, col_size, algorithm, status, grid_map_id, save_mode)
                    VALUES (?,?,?,?, 'RUNNING', ?, 'trie')
                    """;
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setInt(2, info.rowCount());
                ps.setInt(3, info.colCount());
                ps.setString(4, info.algorithm());
                ps.setInt(5, gridMapId);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    runId = keys.getLong(1);
                }
            }
            batchConnection = dataSource.getConnection();
            batchConnection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new IllegalStateException("trie beginRun: " + e.getMessage(), e);
        }
    }

    @Override
    public void onRoot(int x, int y) {
        // Onceki kok (ve stack'te kalan her sey) alt-agaci tamam → flush/atla.
        while (!pathStack.isEmpty()) {
            finish(pathStack.pop());
        }
        Node root = new Node(nextNodeId++, null, 1, x, y, null, solutionsFound + 1);
        pathStack.push(root);
    }

    @Override
    public void onForward(int step, int x, int y, int move) {
        Node parent = pathStack.peek();
        Node node = new Node(nextNodeId++, parent == null ? null : parent.id,
                step, x, y, move, solutionsFound + 1);
        pathStack.push(node);
    }

    @Override
    public void onBackward(int steps) {
        for (int i = 0; i < steps && !pathStack.isEmpty(); i++) {
            finish(pathStack.pop());
        }
    }

    /** Tam cozum bulundu: yigindaki her dugume +1, yaprak isaretle. */
    @Override
    public void accept(FoundSolution solution) {
        boolean leaf = true;
        for (Node n : pathStack) {          // yaprak -> kok
            n.subtreeCount++;
            if (leaf) {
                n.leaf = true;
                leaf = false;
            }
        }
        solutionsFound++;
    }

    @Override
    public void endRun(RunResult result) {
        while (!pathStack.isEmpty()) {
            finish(pathStack.pop());
        }
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
            throw new IllegalStateException("trie endRun: " + e.getMessage(), e);
        }
    }

    /** Test/rapor icin: saklanan (budanmamis) dugum sayisi. */
    public long nodesKept() {
        return nodesKept;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (!runFinished) {
                while (!pathStack.isEmpty()) {
                    finish(pathStack.pop());
                }
                flush();
            }
        } catch (RuntimeException ignored) {
        }
        closeBatchConnection();
        dataSource.close();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
        }
    }

    // --- ic ---

    /** Yigindan cikan dugum: cozume goturuyorsa buffer'a, degilse at. */
    private void finish(Node node) {
        if (node.subtreeCount > 0) {
            flushBuffer.add(node);
            nodesKept++;
            if (flushBuffer.size() >= batchSize) {
                flush();
            }
        }
        // subtreeCount == 0 → cikmaz dal, saklanmaz.
    }

    private void flush() {
        if (flushBuffer.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = batchConnection.prepareStatement(INSERT_STEP)) {
            for (Node n : flushBuffer) {
                ps.setLong(1, n.id);
                ps.setLong(2, runId);
                ps.setInt(3, gridMapId);
                if (n.parentId == null) {
                    ps.setNull(4, Types.BIGINT);
                } else {
                    ps.setLong(4, n.parentId);
                }
                ps.setInt(5, n.stepNo);
                ps.setInt(6, n.x);
                ps.setInt(7, n.y);
                if (n.move == null) {
                    ps.setNull(8, Types.SMALLINT);
                } else {
                    ps.setInt(8, n.move);
                }
                ps.setLong(9, n.solutionOrdinal);
                ps.setLong(10, n.subtreeCount);
                ps.setBoolean(11, n.leaf);
                ps.addBatch();
            }
            ps.executeBatch();
            batchConnection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("trie flush: " + e.getMessage(), e);
        } finally {
            flushBuffer.clear();
        }
    }

    private int resolveGridMapId(Connection c, int rows, int cols) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM grid_map WHERE row_size = ? AND col_size = ?")) {
            ps.setInt(1, rows);
            ps.setInt(2, cols);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        // bilinmeyen boyut → yeni id ekle (mevcut max + 1)
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM grid_map")) {
            rs.next();
            int newId = rs.getInt(1);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO grid_map (id, row_size, col_size) VALUES (?,?,?)")) {
                ps.setInt(1, newId);
                ps.setInt(2, rows);
                ps.setInt(3, cols);
                ps.executeUpdate();
            }
            return newId;
        }
    }

    private void onJvmShutdown() {
        if (closed || runFinished || dataSource.isClosed()) {
            return;
        }
        try {
            while (!pathStack.isEmpty()) {
                finish(pathStack.pop());
            }
            flush();
        } catch (RuntimeException ignored) {
        }
        if (runId > 0) {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE solver_run SET status='ABORTED', finished_at=now() WHERE id=? AND status='RUNNING'")) {
                ps.setLong(1, runId);
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        }
        closeBatchConnection();
        dataSource.close();
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

    private static final class Node {
        final long id;
        final Long parentId;
        final int stepNo;
        final int x;
        final int y;
        final Integer move;
        final long solutionOrdinal;
        long subtreeCount = 0;
        boolean leaf = false;

        Node(long id, Long parentId, int stepNo, int x, int y, Integer move, long solutionOrdinal) {
            this.id = id;
            this.parentId = parentId;
            this.stepNo = stepNo;
            this.x = x;
            this.y = y;
            this.move = move;
            this.solutionOrdinal = solutionOrdinal;
        }
    }
}
