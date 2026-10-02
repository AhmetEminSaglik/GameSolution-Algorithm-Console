package persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * {@code grid_map.id} cozumleme - flat ({@link JdbcSolutionSink}) ve trie
 * ({@link TrieSolutionSink}) ayni id'yi kullanir. Bilinmeyen boyut icin yeni satir
 * eklenir (mevcut max + 1); partition'i yoksa veri DEFAULT partition'a duser.
 */
final class GridMapIds {

    private GridMapIds() {
    }

    static int resolve(Connection c, int rows, int cols) throws SQLException {
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
}
