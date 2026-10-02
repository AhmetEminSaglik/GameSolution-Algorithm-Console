package persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * {@code run_result} istatistigini (bkz. docker/initdb/12_machine_and_run_result.sql)
 * bir grid icin yeniden hesaplatir. Hata cozucuyu durdurmaz - rapor sonradan
 * {@code SELECT refresh_run_result();} ile de hesaplanabilir.
 */
public final class RunResults {

    private RunResults() {
    }

    public static void refresh(Connection c, int gridMapId) {
        try (PreparedStatement ps = c.prepareStatement("SELECT refresh_run_result(?::smallint)")) {
            ps.setInt(1, gridMapId);
            ps.execute();
        } catch (SQLException e) {
            System.out.println("[run_result] hesaplanamadi (sonra menuden hesaplanabilir): " + e.getMessage());
        }
    }
}
