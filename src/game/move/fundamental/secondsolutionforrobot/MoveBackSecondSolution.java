package game.move.fundamental.secondsolutionforrobot;

import game.Game;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.second.exitsituation.ExitSituation;
import game.gamerepo.player.robot.solution.second.navigation.Navigation;
import game.move.fundamental.MoveBack;

public class MoveBackSecondSolution extends MoveBack {
    @Override
    public boolean isRequiredToChangeStartLocation() {
        if (game.getPlayer().getStep() == 1 && getClass().equals(MoveBackSecondSolution.class))
            return true;
        return false;
    }

    Robot robot = (Robot) game.getPlayer();
    Navigation navigation;

    @Override
    public void prepareAllStuff() {
        navigation = robot.getRobotMemory().getRoadMemory().getOneWayListLastItem();
    }

    public MoveBackSecondSolution(Game game) {
        super(game);
    }

    @Override
    public void updateBeforeStep() {
        super.updateBeforeStep();
    }

    @Override
    public void updateAfterStep() {
        super.updateAfterStep();
        doIfThereAreThingsTodoInOneWayNumberProcess();
        clearLastIndexInNavigationList();
    }

    void clearLastIndexInNavigationList() {
        if (robot.getRobotMemory().getRoadMemory().getOneWayListLastItem() != null)
            if (robot.getStep() < robot.getRobotMemory().getRoadMemory().getOneWayListLastItem().getStep()) {
                robot.getRobotMemory().getRoadMemory().removeOneWayListLastItem();
                if (navigation.isExitSituationWasLocatedInThisStep())
                    robot.getRobotMemory().getRoadMemory().updateExistSituation(ExitSituation.EXIT_FREE);
            }
    }

    void doIfThereAreThingsTodoInOneWayNumberProcess() {
        if (!isNavigationNull() && navigation.getStep() == robot.getStep()) {
            processAccordingToOneWayNumber();
        }
    }

    void processAccordingToOneWayNumber() {
        if (robot.getRobotMemory().getRoadMemory().getExitSituation().getSituation() == ExitSituation.EXIT_FREE) {
            if (navigation.getOneWayNumbersValue() == 1) {
                robot.getRobotMemory().getRoadMemory().updateExistSituation(ExitSituation.EXIT_LOCATED);
                navigation.setExitSituationWasLocatedInThisStep(true);
            }
        }

    }

    boolean isNavigationNull() {
        if (navigation == null)
            return true;
        return false;
    }

    @Override
    public String toString() {
        return "MoveBackSecondSolution{}";
    }
}
