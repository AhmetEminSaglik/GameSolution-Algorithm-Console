package game.gamerepo.player.robot.solution.second.navigation;

import game.Game;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.second.exitsituation.ExitSituation;
import game.location.DirectionLocation;

public class NavigationService {

    public void setCompulsoryLocationToNavigation(Game game, Navigation navigation, DirectionLocation lastLocation) {
        Robot robot = getRobot(game);

        if (navigation.getOneWayNumbersValue() == 1 &&
                robot.getRobotMemory().getRoadMemory().getExitSituation().getSituation() == ExitSituation.EXIT_LOCATED
                && !navigation.isExitSituationWasLocatedInThisStep()) {
            navigation.setCompulsoryLocation(lastLocation);
        }


    }

    public DirectionLocation getCompulsoryLocation(Navigation navigation) {
        DirectionLocation selectedDirection = navigation.getCompulsoryLocation();
        if (selectedDirection == null)
            throw new NullPointerException("Compulsory Location Is Null");

        return navigation.getCompulsoryLocation();
    }

    public Navigation buildNavigation(Game game, int oneWayNumbersValue, DirectionLocation compulsoryLocation) {
        Navigation navigation = new Navigation();

        navigation.setStep(getRobot(game).getStep());
        navigation.setOneWayNumbersValue(oneWayNumbersValue);
        if (compulsoryLocation != null) {
            navigation.setCompulsoryLocation(compulsoryLocation);
        }

        return navigation;
    }

    public void addNavigationToRoadMemoryList(Navigation navigation, Robot robot) {
        robot.getRobotMemory().getRoadMemory().getOneWayNumbersList().add(navigation);
    }

    private Robot getRobot(Game game) {
        return (Robot) game.getPlayer();
    }
}
