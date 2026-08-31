package persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * "Su acilistan (ilk birkac adim) itibaren kac cozum var?" sorgusu.
 *
 * Calistir:
 *   java -cp target/game-solution-algorithm.jar persistence.OpeningStats 5 5
 *   java -cp target/game-solution-algorithm.jar persistence.OpeningStats 5 5 30
 *
 * Cikti: ilk 3 adimin (x,y) koordinatlari + o acilisla biten cozum sayisi,
 * en cok cozumden aza dogru.
 */
public final class OpeningStats {

    private OpeningStats() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Kullanim: OpeningStats <rows> <cols> [topN=20]");
            return;
        }
        int rows = Integer.parseInt(args[0]);
        int cols = Integer.parseInt(args[1]);
        int topN = (args.length > 2) ? Integer.parseInt(args[2]) : 20;
        int gridSize = rows * 1000 + cols;

        DbConfig cfg = DbConfig.load();
        String sql = """
                SELECT open1, open2, open3, COUNT(*) AS cnt
                  FROM path_explorer_solution
                 WHERE grid_size = ?
                 GROUP BY open1, open2, open3
                 ORDER BY cnt DESC
                 LIMIT ?
                """;
        try (Connection c = DriverManager.getConnection(cfg.url(), cfg.user(), cfg.password());
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, gridSize);
            ps.setInt(2, topN);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf("%-10s %-10s %-10s %12s%n", "adim1", "adim2", "adim3", "cozum");
                long total = 0;
                while (rs.next()) {
                    String s1 = xy(rs.getObject("open1"), cols);
                    String s2 = xy(rs.getObject("open2"), cols);
                    String s3 = xy(rs.getObject("open3"), cols);
                    long cnt = rs.getLong("cnt");
                    total += cnt;
                    System.out.printf("%-10s %-10s %-10s %12d%n", s1, s2, s3, cnt);
                }
                System.out.println("(ilk " + topN + " acilisin toplami: " + total + ")");
            }
        }
    }

    /** Hucre indeksi (x*cols + y) -> "(x,y)" ; null -> "-". */
    static String xy(Object cellIndex, int cols) {
        if (cellIndex == null) {
            return "-";
        }
        int idx = ((Number) cellIndex).intValue();
        return "(" + (idx / cols) + "," + (idx % cols) + ")";
    }
}
