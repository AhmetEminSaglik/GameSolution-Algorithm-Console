package persistence;

import game.Game;
import game.gamerepo.BuildGame;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.second.SecondSolution_CalculateForwardAvailableWays;
import game.play.PlayGame;
import game.play.input.robot.RobotInput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import support.GameHarness;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PlayGame -> SolutionSink kancasinin gercek cozucu ciktisiyla dogru calistigini
 * dogrular: 5x5'te 12_400 cozum gelmeli, her yol 25 hucre + gecerli sicramalar +
 * dogru baslangic. Ayni zamanda PathCodec'i gercek verilerle round-trip test eder.
 */
class SolutionSinkHookTest {

    private static final int[][] DIRS = {
            {0, 3}, {2, 2}, {3, 0}, {2, -2}, {0, -3}, {-2, -2}, {-3, 0}, {-2, 2}
    };

    @AfterAll
    static void cleanup() {
        GameHarness.cleanGeneratedFiles(5);
    }

    @Test
    void secondSolution_5x5_emitsEveryValidSolutionPath() {
        RecordingSink sink = new RecordingSink();

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
        }

        assertEquals(1, sink.beginRuns);
        assertEquals(1, sink.endRuns);
        assertEquals(12_400, sink.solutions.size(), "5x5'te 12_400 cozum yakalanmali");
        assertEquals(12_400L, sink.lastResult.totalSolved());

        long idx = 0;
        for (SolutionSink.FoundSolution fs : sink.solutions) {
            assertEquals(++idx, fs.solutionIndex(), "solutionIndex artan olmali");
            GridPath p = fs.path();
            assertEquals(25, p.length(), "5x5 yol 25 hucre");
            assertEquals(p.cells()[0][0], p.startX());
            assertEquals(p.cells()[0][1], p.startY());
            assertTrue(isValidWalk(p.cells(), 5, 5), "ardisik hucreler gecerli sicrama olmali, tekrar yok");
            // PathCodec round-trip
            byte[] enc = p.encode();
            int[][] dec = PathCodec.decode(enc, p.startX(), p.startY(), p.length());
            for (int k = 0; k < 25; k++) {
                assertEquals(p.cells()[k][0], dec[k][0]);
                assertEquals(p.cells()[k][1], dec[k][1]);
            }
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
            if (!ok) return false;
            int x = cells[k][0];
            int y = cells[k][1];
            if (x < 0 || y < 0 || x >= rows || y >= cols || seen[x][y]) return false;
            seen[x][y] = true;
        }
        return true;
    }

    private static final class RecordingSink implements SolutionSink {
        int beginRuns;
        int endRuns;
        final List<FoundSolution> solutions = new ArrayList<>();
        RunResult lastResult;

        @Override
        public void beginRun(RunInfo info) {
            beginRuns++;
        }

        @Override
        public void accept(FoundSolution solution) {
            solutions.add(solution);
        }

        @Override
        public void endRun(RunResult result) {
            endRuns++;
            lastResult = result;
        }

        @Override
        public void close() {
        }
    }
}
