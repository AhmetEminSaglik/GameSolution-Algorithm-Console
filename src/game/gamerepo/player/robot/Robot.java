package game.gamerepo.player.robot;

import compass.Compass;
import compass.DirectionCompass;
import game.Game;
import game.gameover.RobotGameOver;
import game.gamerepo.player.Player;
import game.gamerepo.player.robot.memory.RobotMemory;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.location.DirectionLocation;
import game.play.PlayerMove;
import game.rule.BaseGameRule;
import print.FileWriteProcess;


public class Robot extends Player {
    DirectionCompass compass = new DirectionCompass();
    private RobotMemory robotMemory;
    private BaseSolution solution;
    int recordValueForEachSquare;

    public Robot() {
        robotMemory = new RobotMemory(game);
        printAbleEveryStep = false;
    }


    @Override
    public Compass getCompass() {
        return compass;
    }

    @Override
    public String getSolutionName() {
        return getSolution().getClass().getSimpleName();
    }

    public BaseSolution getSolution() {
        return solution;
    }

    public void setSolution(BaseSolution solution) {
        this.solution = solution;
        solution.buildRobotMove();

        setName(solution.getSolutionFileName());
        setPlayerMove();
        initSolutionFiles();
    }

    private void initSolutionFiles() {
        printableFileScore = new FileWriteProcess(getSolution().getSolutionFileName() + "_Completed");
        printableFileTotalScoreCount = new FileWriteProcess(getSolution().getSolutionFileName() + "_EverySingleSquareTotalValue");
    }

    @Override
    public BaseGameRule getGameRule() {
        if (gameRule == null) {
            gameRule = new BaseGameRule(new RobotGameOver(getGame()));
        }
        return gameRule;
    }

    @Override
    public void updateVisitedDirection(boolean sealOrUnseal, int step, DirectionLocation location) {
        assert (getStep() > 1) : getClass().getName() + " >>> ADIM SAYUISI " + getStep() + " GELDI";
        location.setCompass(getGame().getPlayer().getCompass());
        getVisitedDirections()[step][location.getId()] = sealOrUnseal;
    }

    @Override
    public void setPlayerMove() {
        playerMove = new PlayerMove(solution.getMoveForward(), solution.getMoveBack());
    }

    public int getRecordValueForEachSquare() {
        return recordValueForEachSquare;
    }

    public void increaseRecordValueForEachSquare() {
        recordValueForEachSquare++;
    }

    public void decreaseRecordValueForEachSquare() {
        recordValueForEachSquare--;
    }

    public void resetRecordValueForEachSquare() {
        recordValueForEachSquare = 0;
    }

    public RobotMemory getRobotMemory() {
        return robotMemory;
    }

    @Override
    public boolean isPrintableStepSituation() {
        return printAbleEveryStep;
    }
}
