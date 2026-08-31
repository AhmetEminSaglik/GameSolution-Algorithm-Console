package game.move.fundamental.secondsolutionforrobot;

import game.Game;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.second.exitsituation.ExitSituation;
import game.gamerepo.player.robot.solution.second.navigation.Navigation;
import game.location.DirectionLocation;
import game.location.LocationsList;
import game.move.fundamental.MoveForward;

public class MoveForwardSecondSolution extends MoveForward {
    public MoveForwardSecondSolution(Game game) {
        super(game);
    }

    Robot robot = (Robot) game.getPlayer();
    Navigation navigation;

//    DirectionLocation lastLocation = new LocationsList().getLastLocation(robot.getCompass());

    @Override
    public void prepareAllStuff() {
        navigation = robot.getRobotMemory().getRoadMemory().getOneWayListLastItem();
    }

    @Override
    public void updateBeforeStep() {
        super.updateBeforeStep();
        doIfThereAreThingsTodoInOneWayNumberProcess();
    }

    @Override
    public void updateAfterStep() {
        super.updateAfterStep();

    }

    void doIfThereAreThingsTodoInOneWayNumberProcess() {
        if (!isNavigationNull() && navigation.getStep() == robot.getStep()) {
            processAccordingToOneWayNumber();
        }
    }


    boolean isDirectionSame(DirectionLocation d1, DirectionLocation d2) {

        if (d1.getX() == d2.getX() && d1.getY() == d2.getY()) {
            return true;
        }
        return false;

    }

    void processAccordingToOneWayNumber() {

        if (navigation.getOneWayNumbersValue() == 2) {
            if (robot.getRobotMemory().getRoadMemory().getExitSituation().getSituation() == ExitSituation.EXIT_FREE) {
                locateExitSituation();
            }

            if (isDirectionSame(getDirectionLocation(), navigation.getCompulsoryLocation())) {
                navigation.setCompulsoryLocation(new LocationsList().getLastLocation(robot.getCompass()));

            }
        } else if (navigation.getOneWayNumbersValue() == 1 &&
                robot.getRobotMemory().getRoadMemory().getExitSituation().getSituation() == ExitSituation.EXIT_LOCATED &&
                !navigation.isExitSituationWasLocatedInThisStep()) {
            navigation.setCompulsoryLocation(new LocationsList().getLastLocation(robot.getCompass()));
        }
    }

    void locateExitSituation() {
        robot.getRobotMemory().getRoadMemory().updateExistSituation(ExitSituation.EXIT_LOCATED);
        navigation.setExitSituationWasLocatedInThisStep(true);
    }

    boolean isNavigationNull() {
        if (navigation == null)
            return true;
        return false;
    }

    @Override
    public String toString() {
        return "MoveForwardSecondSolution{}";
    }
}
