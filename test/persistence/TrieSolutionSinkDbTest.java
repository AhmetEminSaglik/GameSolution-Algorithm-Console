package persistence;

import game.Game;
import game.gamerepo.BuildGame;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.second.SecondSolution_CalculateForwardAvailableWays;
import game.play.PlayGame;
import game.play.input.robot.RobotInput;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import support.GameHarness;

import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Trie (parent-child agac) sink'i gercek Postgres'e karsi.
 *   docker compose up -d ; mvn test -Dtest.excludedGroups=slow -Dgroups=db
 *
 * 5x5 2. cozum → trie. Dogrular:
 *  - Σ kok subtree_solution_count = 12_400 (= toplam cozum)
 *  - dugum sayisi + sikisma orani yazdirilir
 *  - bir kok'un cocuklarinin count toplami = kok'un count'u (parent-child tutarlilik)
 *  - bir yapraktan koke yol geri kurulunca gecerli 25-hucre cozum
 */
@Tag("db")
class TrieSolutionSinkDbTest {

    private static final int[][] DIRS = {
            {0, 3}, {2, 2}, {3, 0}, {2, -2}, {0, -3}, {-2, -2}, {-3, 0}, {-2, 2}
    };

    @Test
    void trie5x5_matchesFlatCountAndReconstructs() throws Exception {
        DbConfig cfg = DbConfig.load();
        assumeTrue(reachable(cfg), "Postgres yok — test atlandi");

        // trie artik (grid_map_id, algorithm_id) bazinda tekil (ON CONFLICT DO NOTHING).
        // Onceki calismanin birakmis olabilecegi 5x5 + algo2 dugumlerini temizle ki
        // bu kosu her satiri gercekten yazsin.
        try (Connection c = DriverManager.getConnection(cfg.url(), cfg.user(), cfg.password());
             var ps = c.prepareStatement("DELETE FROM solution_step WHERE grid_map_id = 1 AND algorithm_id = 2")) {
            ps.executeUpdate();
        }

        long runId;
        TrieSolutionSink sink = new TrieSolutionSink(cfg);
        PrintStream original = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            BuildGame build = new BuildGame(5);
            Game game = build.createGame();
            Robot robot = new Robot();
            robot.setGame(game);
            robot.setSolution(new SecondSolution_CalculateForwardAvailableWays(game));
            robot.setIPlayerInput(new RobotInput(robot.getSolution(), game));
            build.createVisitedArea();
            new PlayGame(game, sink).playGame();
        } finally {
            System.setOut(original);
            sink.close();
        }

        try (Connection c = DriverManager.getConnection(cfg.url(), cfg.user(), cfg.password())) {
            try (var st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT id, total_solved, save_mode, status FROM solver_run ORDER BY id DESC LIMIT 1")) {
                rs.next();
                runId = rs.getLong("id");
                assertEquals("trie", rs.getString("save_mode"));
                assertEquals("COMPLETED", rs.getString("status"));
                assertEquals(12_400L, rs.getLong("total_solved"));
            }

            long nodeCount;
            try (var ps = c.prepareStatement("SELECT COUNT(*) FROM solution_step WHERE run_id = ?")) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    nodeCount = rs.getLong(1);
                }
            }
            System.out.printf("[trie] 5x5: %d dugum  (duz depolama ~12_400 cozum x 24 hamle = 297_600 slot; oran %.2fx)%n",
                    nodeCount, 297_600.0 / nodeCount);
            assertTrue(nodeCount > 0);
            assertEquals(sink.nodesKept(), nodeCount, "sink.nodesKept() == DB satir sayisi");

            // Σ kok subtree_solution_count = 12_400
            long rootSum;
            try (var ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(subtree_solution_count),0) FROM solution_step WHERE run_id = ? AND parent_step_id IS NULL")) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    rootSum = rs.getLong(1);
                }
            }
            assertEquals(12_400L, rootSum, "kok dugumlerin toplam cozum sayisi = 12_400");

            // parent-child tutarlilik: bir kok'un cocuk count toplami = kok count
            try (var ps = c.prepareStatement("""
                    SELECT r.subtree_solution_count AS root_cnt,
                           COALESCE((SELECT SUM(ch.subtree_solution_count) FROM solution_step ch
                                     WHERE ch.run_id = r.run_id AND ch.parent_step_id = r.id), 0) AS child_sum
                      FROM solution_step r
                     WHERE r.run_id = ? AND r.parent_step_id IS NULL
                     ORDER BY r.id LIMIT 1
                    """)) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long rootCnt = rs.getLong("root_cnt");
                    long childSum = rs.getLong("child_sum");
                    // kok'un kendisi yaprak degil; cocuklarin toplami == kok count olmali
                    assertEquals(rootCnt, childSum, "kok count = cocuklarin count toplami");
                }
            }

            // bir yapraktan koke yol geri kur → gecerli 25-hucre cozum
            int[][] path = reconstructOneSolution(c, runId);
            assertEquals(25, path.length);
            assertTrue(isValidWalk(path, 5, 5), "geri kurulan yol gecerli sicramalardan olusmali");

            // temizlik
            try (var ps = c.prepareStatement("DELETE FROM solution_step WHERE run_id = ?")) {
                ps.setLong(1, runId);
                ps.executeUpdate();
            }
            try (var ps = c.prepareStatement("DELETE FROM solver_run WHERE id = ?")) {
                ps.setLong(1, runId);
                ps.executeUpdate();
            }
        }
        GameHarness.cleanGeneratedFiles(5);
    }

    /** Bir yaprak sec, parent zinciriyle koke cik, move'lari topla, baslangictan oyna. */
    private static int[][] reconstructOneSolution(Connection c, long runId) throws Exception {
        long leafId;
        int startX;
        int startY;
        try (var ps = c.prepareStatement(
                "SELECT id FROM solution_step WHERE run_id = ? AND is_leaf ORDER BY id LIMIT 1")) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                leafId = rs.getLong(1);
            }
        }
        List<Integer> movesLeafToRoot = new ArrayList<>();
        long cur = leafId;
        Integer parent;
        int rootX = -1;
        int rootY = -1;
        do {
            try (var ps = c.prepareStatement(
                    "SELECT parent_step_id, move_from_parent, x, y FROM solution_step WHERE run_id = ? AND id = ?")) {
                ps.setLong(1, runId);
                ps.setLong(2, cur);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long p = rs.getLong("parent_step_id");
                    parent = rs.wasNull() ? null : (int) p;
                    int mv = rs.getInt("move_from_parent");
                    if (!rs.wasNull()) {
                        movesLeafToRoot.add(mv);
                    } else {
                        rootX = rs.getInt("x");
                        rootY = rs.getInt("y");
                    }
                }
            }
            if (parent != null) {
                cur = parent;
            }
        } while (parent != null);

        int[][] cells = new int[movesLeafToRoot.size() + 1][2];
        cells[0][0] = rootX;
        cells[0][1] = rootY;
        // movesLeafToRoot: yapraktan koke → ters cevir (kokten yaprağa)
        for (int i = 0; i < movesLeafToRoot.size(); i++) {
            int mv = movesLeafToRoot.get(movesLeafToRoot.size() - 1 - i);
            cells[i + 1][0] = cells[i][0] + DIRS[mv][0];
            cells[i + 1][1] = cells[i][1] + DIRS[mv][1];
        }
        return cells;
    }

    private static boolean reachable(DbConfig cfg) {
        try (Connection c = DriverManager.getConnection(cfg.url(), cfg.user(), cfg.password())) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isValidWalk(int[][] cells, int rows, int cols) {
        boolean[][] seen = new boolean[rows][cols];
        seen[cells[0][0]][cells[0][1]] = true;
        for (int k = 1; k < cells.length; k++) {
            int dx = cells[k][0] - cells[k - 1][0];
            int dy = cells[k][1] - cells[k - 1][1];
            boolean ok = false;
            for (int[] d : DIRS) {
                if (d[0] == dx && d[1] == dy) {
                    ok = true;
                    break;
                }
            }
            int x = cells[k][0];
            int y = cells[k][1];
            if (!ok || x < 0 || y < 0 || x >= rows || y >= cols || seen[x][y]) {
                return false;
            }
            seen[x][y] = true;
        }
        return true;
    }
}
