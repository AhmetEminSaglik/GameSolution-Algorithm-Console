package persistence.checkpoint;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import persistence.DbConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code solving_checkpoint} + {@code grid_map} okur. Replay motoru bunu kullanir.
 */
final class Algo2CheckpointLoader implements AutoCloseable {

    private static final String SELECT_COLS = """
            c.solution_index, g.row_size, g.col_size, c.algorithm_id, c.algorithm_version,
            c.interval_size, c.step, c.dir_count, c.path, c.visited_dirs, c.exit_situation,
            c.one_way_list, c.round_counter, c.round_counter_overlong, c.total_solved,
            c.total_solved_overlong, c.total_back_steps, c.dummy_back_steps, c.locked_back_lose,
            c.square_total_solved
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

    /** Bir kosunun herhangi bir checkpoint'i var mi + kac tane. */
    long count(UUID runId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM solving_checkpoint WHERE solving_run_id = ?")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("checkpoint sayilamadi: " + e.getMessage(), e);
        }
    }

    /** {@code solution_index <= target} olan EN SON checkpoint. */
    Optional<Algo2CheckpointRow> latestAtOrBefore(UUID runId, long target) {
        return queryOne(
                "SELECT " + SELECT_COLS + """
                 FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
                 WHERE c.solving_run_id = ? AND c.solution_index <= ?
                 ORDER BY c.solution_index DESC LIMIT 1
                 """, runId, target);
    }

    /** {@code solution_index > after} olan EN KUCUK checkpoint (verify icin "sonraki"). */
    Optional<Algo2CheckpointRow> firstAfter(UUID runId, long after) {
        return queryOne(
                "SELECT " + SELECT_COLS + """
                 FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
                 WHERE c.solving_run_id = ? AND c.solution_index > ?
                 ORDER BY c.solution_index ASC LIMIT 1
                 """, runId, after);
    }

    Optional<Algo2CheckpointRow> exact(UUID runId, long index) {
        return queryOne(
                "SELECT " + SELECT_COLS + """
                 FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
                 WHERE c.solving_run_id = ? AND c.solution_index = ?
                 """, runId, index);
    }

    private Optional<Algo2CheckpointRow> queryOne(String sql, UUID runId, long idx) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setLong(2, idx);
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
                rs.getInt("algorithm_version"),
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
                rs.getBoolean("locked_back_lose"),
                rs.getInt("square_total_solved"));
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
