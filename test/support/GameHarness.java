package support;

import game.Game;
import game.gamerepo.BuildGame;
import game.gamerepo.player.Score;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.gamerepo.player.robot.solution.first.FirstSolution_Combination;
import game.gamerepo.player.robot.solution.second.SecondSolution_CalculateForwardAvailableWays;
import game.play.PlayGame;
import game.play.input.robot.RobotInput;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Oyunu menu / Scanner olmadan, kod icinden calistiran test yardimcisi.
 * {@code Main.main}'in Robot yolunu birebir tekrarlar (BuildGame -> createGame
 * -> Robot+Solution baglama -> createVisitedArea -> PlayGame.playGame).
 *
 * System.out bastirilir (oyun sonu ozet satirlari test ciktisini kirletmesin).
 * Oyun {@code Solution-N-KxK_*.txt} dosyalarini calisma dizinine yazar; bunlar
 * .gitignore kapsaminda ve {@link #cleanGeneratedFiles(int)} ile silinebilir.
 */
public final class GameHarness {

    public static final int FIRST_SOLUTION = 1;
    public static final int SECOND_SOLUTION = 2;

    private GameHarness() {
    }

    public record Result(long totalSolved, long roundCounter, long dummyBackStep, long totalBackStep) {
        @Override
        public String toString() {
            return "solved=" + totalSolved + " rounds=" + roundCounter
                    + " dummyBack=" + dummyBackStep + " totalBack=" + totalBackStep;
        }
    }

    public static Result runRobot(int edge, int solutionOrder) {
        return runRobot(edge, edge, solutionOrder);
    }

    public static Result runRobot(int rows, int cols, int solutionOrder) {
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            BuildGame build = new BuildGame(rows, cols);
            Game game = build.createGame();

            Robot robot = new Robot();
            robot.setGame(game);
            BaseSolution solution = (solutionOrder == FIRST_SOLUTION)
                    ? new FirstSolution_Combination(game)
                    : new SecondSolution_CalculateForwardAvailableWays(game);
            robot.setSolution(solution);
            robot.setIPlayerInput(new RobotInput(robot.getSolution(), game));

            build.createVisitedArea();

            new PlayGame(game).playGame();

            Score s = robot.getScore();
            return new Result(
                    s.getTotalGameFinishedScore(),
                    game.getRoundCounter(),
                    s.getCounterOfDummyBackMove(),
                    s.getCounterTotalBackStep());
        } finally {
            System.setOut(originalOut);
        }
    }

    public static void cleanGeneratedFiles(int edge) {
        cleanGeneratedFiles(edge, edge);
    }

    /** Test kosusunun urettigi Solution-*-{rows}x{cols}-*.txt dosyalarini siler. */
    public static void cleanGeneratedFiles(int rows, int cols) {
        String tag = rows + "x" + cols;
        try (Stream<Path> files = Files.list(Path.of("."))) {
            files.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("Solution-") && n.contains(tag) && n.endsWith(".txt");
            }).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
