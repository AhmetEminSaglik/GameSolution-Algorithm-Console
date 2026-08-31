package game.move.fundamental.thirdsolution;

import game.Game;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.second.exitsituation.ExitSituation;
import game.gamerepo.player.robot.solution.second.navigation.Navigation;
import game.location.DirectionLocation;
import game.location.LocationsList;
import game.move.fundamental.MoveForward;
import print.EasylyReadNumber;
import printarray.StringFormat;

public class MoveForwardThirdSolution extends MoveForward {
    public MoveForwardThirdSolution(Game game) {
        super(game);
    }

    Robot robot = (Robot) game.getPlayer();
    Navigation navigation;

    DirectionLocation lastLocation = new LocationsList().getLastLocation(robot.getCompass());

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
//        printGamelastStuation(game);
    }
  /*  String getEasyReadyNumber(long number) {
        return new EasylyReadNumber().getReadableNumberInStringFormat(number);
    }
    void printGamelastStuation(Game game) {
        String textWillAppendToFile = " Finished totalGame : " + game.getPlayer().getScore().getTotalGameFinishedScore() + "\n";
        textWillAppendToFile += "RoundCounter : " + getEasyReadyNumber(game.getRoundCounter()) + '\n' + "" +
                "Counter of Moving Back "  +getEasyReadyNumber(game.getPlayer().getScore().getCounterOfMovingBackLose())+"\n"+
                "Step : " + game.getPlayer().getStep() + "\n";


        textWillAppendToFile += new StringFormat().getStringFormatArray(game.getModel().getGameSquares());//  print game squares
        System.out.println(textWillAppendToFile);
        System.out.println();
//        printToFile(textWillAppendToFile);
    }*/

    boolean isNavigationNull() {
        if (navigation == null)
            return true;
        return false;
    }
}
