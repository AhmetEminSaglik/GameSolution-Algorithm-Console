package persistence.checkpoint;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import persistence.DbConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code solving_checkpoint} + {@code grid_map} okur. Anahtar: (row, col, algorithm_id)
 * -- checkpoint'ler ilerleme bazinda tekil, hangi calismanin yazdigina bakilmaz.
 */
final class Algo2CheckpointLoader implements AutoCloseable {

    private static final String SELECT_COLS = """
            c.solution_index, g.row_size, g.col_size, c.algorithm_id,
            c.interval_size, c.step, c.dir_count, c.path, c.visited_dirs, c.exit_situation,
            c.one_way_list, c.round_counter, c.round_counter_overlong, c.total_solved,
            c.total_solved_overlong, c.total_back_steps, c.dummy_back_steps, c.locked_back_lose
            """;
    private static final String FROM_WHERE = """
             FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
             WHERE g.row_size = ? AND g.col_size = ? AND c.algorithm_id = ?
            """;

    private final HikariDataSource dataSource;

    Algo2CheckpointLoader(DbConfig cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.url());
        hc.setUsername(cfg.user());
        hc.setPassword(cfg.password());
        hc.setMaximumPoolSize(1);
        hc.setPoolName("solving-checkpoint-read");
        this.dataSource = new HikariDataSource(hc);
    }

    /** Bu harita + algoritma icin tum checkpoint ozetleri, solution_index artan. */
    List<CheckpointSummary> listSummaries(int row, int col, int algo) {
        String sql = """
                SELECT c.solution_index, c.step, c.round_counter, c.total_solved,
                       c.total_back_steps, c.dummy_back_steps, c.created_at
                  FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
                 WHERE g.row_size = ? AND g.col_size = ? AND c.algorithm_id = ?
                 ORDER BY c.solution_index ASC
                """;
        List<CheckpointSummary> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, row);
            ps.setInt(2, col);
            ps.setInt(3, algo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CheckpointSummary(
                            rs.getLong(1), rs.getInt(2), rs.getLong(3), rs.getLong(4),
                            rs.getLong(5), rs.getLong(6), rs.getTimestamp(7)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("checkpoint listesi alinamadi: " + e.getMessage(), e);
        }
        return out;
    }

    long count(int row, int col, int algo) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id"
                             + " WHERE g.row_size=? AND g.col_size=? AND c.algorithm_id=?")) {
            ps.setInt(1, row);
            ps.setInt(2, col);
            ps.setInt(3, algo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("checkpoint sayilamadi: " + e.getMessage(), e);
        }
    }

    /** Bu harita + algoritma icin EN SON checkpoint ("devam et"). */
    Optional<Algo2CheckpointRow> latest(int row, int col, int algo) {
        return queryOne("SELECT " + SELECT_COLS + FROM_WHERE
                + " ORDER BY c.solution_index DESC LIMIT 1", row, col, algo, null);
    }

    /** {@code solution_index <= target} olan EN SON checkpoint. */
    Optional<Algo2CheckpointRow> latestAtOrBefore(int row, int col, int algo, long target) {
        return queryOne("SELECT " + SELECT_COLS + FROM_WHERE
                + " AND c.solution_index <= ? ORDER BY c.solution_index DESC LIMIT 1", row, col, algo, target);
    }

    /** {@code solution_index > after} olan EN KUCUK checkpoint (verify icin "sonraki"). */
    Optional<Algo2CheckpointRow> firstAfter(int row, int col, int algo, long after) {
        return queryOne("SELECT " + SELECT_COLS + FROM_WHERE
                + " AND c.solution_index > ? ORDER BY c.solution_index ASC LIMIT 1", row, col, algo, after);
    }

    Optional<Algo2CheckpointRow> exact(int row, int col, int algo, long index) {
        return queryOne("SELECT " + SELECT_COLS + FROM_WHERE
                + " AND c.solution_index = ?", row, col, algo, index);
    }

    private Optional<Algo2CheckpointRow> queryOne(String sql, int row, int col, int algo, Long idx) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, row);
            ps.setInt(2, col);
            ps.setInt(3, algo);
            if (idx != null) {
                ps.setLong(4, idx);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("checkpoint okunamadi: " + e.getMessage(), e);
        }
    }

    private static Algo2CheckpointRow map(ResultSet rs) throws SQLException {
        return new Algo2CheckpointRow(
                rs.getLong("solution_index"),
                rs.getInt("row_size"),
                rs.getInt("col_size"),
                rs.getInt("algorithm_id"),
                rs.getInt("interval_size"),
                rs.getInt("step"),
                rs.getInt("dir_count"),
                rs.getBytes("path"),
                rs.getBytes("visited_dirs"),
                rs.getInt("exit_situation"),
                rs.getBytes("one_way_list"),
                rs.getLong("round_counter"),
                rs.getInt("round_counter_overlong"),
                rs.getLong("total_solved"),
                rs.getInt("total_solved_overlong"),
                rs.getLong("total_back_steps"),
                rs.getLong("dummy_back_steps"),
                rs.getBoolean("locked_back_lose"));
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
