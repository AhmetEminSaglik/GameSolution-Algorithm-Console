package persistence.checkpoint;

import game.Game;
import game.gamerepo.BuildGame;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.gamerepo.player.robot.solution.second.SecondSolution_CalculateForwardAvailableWays;
import game.play.input.robot.RobotInput;

/**
 * Standalone (CLI) replay/verify icin taze bir Algoritma 2 oyunu kurar ve verilen
 * checkpoint satirini uzerine restore eder.
 *
 * (Main'in "devam et" akisi kendi kurdugu game'i kullanir; bkz. {@link Algo2ResumeService}.)
 */
final class Algo2GameBuilder {

    private Algo2GameBuilder() {
    }

    static Game buildAndRestore(Algo2CheckpointRow row) {
        BuildGame bg = new BuildGame(row.rowSize(), row.colSize());
        Game game = bg.createGame();
        bg.createVisitedArea();

        Robot robot = new Robot();
        robot.setGame(game);
        BaseSolution solution = new SecondSolution_CalculateForwardAvailableWays(game);
        robot.setSolution(solution);
        robot.setIPlayerInput(new RobotInput(solution, game));
        robot.startTimeKeeper();

        Algo2StateRestorer.restore(game, row);
        return game;
    }
}
