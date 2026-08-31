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
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Gercek Postgres'e karsi entegrasyon testi. Calisan bir DB ister:
 *   docker compose up -d
 * Calistir:  mvn test -Dgroups=db
 * DB ulasilamazsa test ATLANIR (assumeTrue).
 *
 * 5x5 2. cozumu JdbcSolutionSink ile kosar, sonra dogrudan SQL ile dogrular:
 *  - solver_run: 1 satir, status COMPLETED, total_solved = 12_400
 *  - path_explorer_solution: grid_size=5005 icin 12_400 satir
 *  - bir satirin path'i decode edilince gecerli 25-hucre yol
 * Sonunda test verisini siler.
 */
@Tag("db")
class JdbcSolutionSinkDbTest {

    private static final int[][] DIRS = {
            {0, 3}, {2, 2}, {3, 0}, {2, -2}, {0, -3}, {-2, -2}, {-3, 0}, {-2, 2}
    };

    @Test
    void writes5x5SolutionsToPostgres() throws Exception {
        DbConfig cfg = DbConfig.load();
        assumeTrue(reachable(cfg), "Postgres ulasilamiyor (docker compose up -d?) — test atlandi");

        long runId;
        JdbcSolutionSink sink = new JdbcSolutionSink(cfg);
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
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT id, status, total_solved, round_counter FROM solver_run ORDER BY id DESC LIMIT 1")) {
                assertTrue(rs.next(), "solver_run satiri olmali");
                runId = rs.getLong("id");
                assertEquals("COMPLETED", rs.getString("status"));
                assertEquals(12_400L, rs.getLong("total_solved"));
                assertEquals(1_023_656L, rs.getLong("round_counter"));
            }

            try (var ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM path_explorer_solution WHERE solver_run_id = ?")) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(12_400L, rs.getLong(1), "12_400 cozum satiri yazilmali");
                }
            }

            // bir satiri decode et, gecerli yol mu?
            try (var ps = c.prepareStatement(
                    "SELECT start_x, start_y, path_len, path FROM path_explorer_solution WHERE solver_run_id = ? LIMIT 1")) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    int[][] cells = PathCodec.decode(rs.getBytes("path"),
                            rs.getInt("start_x"), rs.getInt("start_y"), rs.getInt("path_len"));
                    assertEquals(25, cells.length);
                    assertTrue(isValidWalk(cells, 5, 5), "decode edilen yol gecerli sicramalardan olusmali");
                }
            }

            // opening analitigi calisiyor mu?
            try (var ps = c.prepareStatement(
                    "SELECT open1, COUNT(*) FROM path_explorer_solution WHERE solver_run_id = ? GROUP BY open1 ORDER BY 2 DESC LIMIT 1")) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getLong(2) > 0);
                }
            }

            // temizlik
            try (var ps = c.prepareStatement("DELETE FROM path_explorer_solution WHERE solver_run_id = ?")) {
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
