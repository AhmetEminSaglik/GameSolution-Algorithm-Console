    package game.gamerepo.player.robot.solution.second;

import check.forwardlocation.InpectingForwardLocation;
import game.Game;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.second.exitsituation.ExitSituation;
import game.gamerepo.player.robot.solution.second.navigation.Navigation;
import game.gamerepo.player.robot.solution.second.navigation.NavigationService;
import game.location.DirectionLocation;
import game.location.Location;
import game.location.LocationsList;
import squareprocess.SquareProcess;
import weights.WeightOfAvailableWay;

import java.util.ArrayList;

public class MathFunctionForSecondSolution {
    Game game;
    Location playerLocation;
    ArrayList<DirectionLocation> locationsList;
    final DirectionLocation lastLocation;
    DirectionLocation selectedDirection;
    SquareProcess squareProcess = new SquareProcess();
    WeightOfAvailableWay weightOfAvailableWay = new WeightOfAvailableWay();
    Robot robot;
    final int edgeValue;
    DirectionLocation compulsoryLocation = null;
    Navigation navigation = new Navigation();
    int oneWayNumbersValue;
    boolean killRequestByAvailableProcessFunction = false;
    NavigationService navigationService = new NavigationService();


    public MathFunctionForSecondSolution(Game game, Location playerLocation) {
        this.game = game;
        this.playerLocation = playerLocation;
        robot = (Robot) game.getPlayer();
        edgeValue = game.getModel().getGameSquares().length;
        locationsList = new LocationsList().getListOfLocationsAccordingToPlayerCompass(game.getPlayer().getCompass());
        lastLocation = new LocationsList().getLastLocation(game.getPlayer().getCompass());
        selectedDirection = lastLocation;
    }


    public int calculateFunctionResult() {

        if (isNavigationInRoadMemoryAvailableForThisStep()) {
            navigationService.setCompulsoryLocationToNavigation(game, navigation, lastLocation);
            DirectionLocation compulsoryDirection = navigation.getCompulsoryLocation();
            if (compulsoryDirection != null) {
                selectedDirection = compulsoryDirection;
                return selectedDirection.getId();
            }
        }

        calculateForwardAvailableDirectionsOfCurrentDirection();

        double calculationOfDeadlyPoint = calculateDeadlyPoint();

        if (calculationOfDeadlyPoint == CalculationDeadlyPoint.IS_FREE_SO_MOVE_FORWARD) {
            navigation = buildNavigation();
            addNavigationToRoadMemoryList();
        }

        return selectedDirection.getId();


    }


    boolean isNavigationInRoadMemoryAvailableForThisStep() {
        navigation = getLastNavigationFromRoadMemory();
        return navigation != null && navigation.getStep() == robot.getStep();
    }

    Navigation getLastNavigationFromRoadMemory() {
        if (robot.getRobotMemory().getRoadMemory().getOneWayNumbersList().size() > 0) {
            return robot.getRobotMemory().getRoadMemory().getOneWayListLastItem();
        }
        return null;
    }


    Navigation buildNavigation() {
        return navigationService.buildNavigation(game, oneWayNumbersValue, compulsoryLocation);
    }


    void calculateForwardAvailableDirectionsOfCurrentDirection() {

        double weightResult = -1;
        InpectingForwardLocation inpectingForwardLocation = new InpectingForwardLocation();

        for (int i = 0; i < locationsList.size() - 1; i++) {
            if (squareProcess.isSquareAvailableToMoveOnIt(game, playerLocation, locationsList.get(i))) {
                DirectionLocation location = locationsList.get(i);

                ArrayList<Location> availableLocationList = inpectingForwardLocation.inspectLocationAndGetAvailableSquareNumbers(game, location);
                int availableWayNumber = availableLocationList.size();

                if (isAvailableWayEqualsToZero(availableWayNumber) && !isNextStepWillBeEqualsToTotalSquareValue()) {
                    selectedDirection = lastLocation;
                    return;
                }
                if (weightOfAvailableWay.getWeightOfDirection()[availableWayNumber] > weightResult) {
                    weightResult = calculateWeightResult(availableWayNumber);
                    selectedDirection = location;
                }
                if (availableWayNumber == 1) {
                    processAccordingToOneWayNumber(location);
                }
                if (isRequestedToKillFunctionByOneWayAvailableNumberProcess()) {

                    return;
                }

            }

        }
    }

    boolean isExitSituationLocated() {
        return robot.getRobotMemory().getRoadMemory().getExitSituation().getSituation() == ExitSituation.EXIT_LOCATED;
    }

    void processAccordingToOneWayNumber(DirectionLocation location) {


        oneWayNumbersValue++;

        if (isExitSituationLocated()) {
            compulsoryLocation = lastLocation;
        }
        if (oneWayNumbersValue == 2) {
            compulsoryLocation = location;
        }
        if (isOneWayNumberTooMuchToRunHealtyTheAlgorithm()) {
            selectedDirection = lastLocation;
            killRequestByAvailableProcessFunction = true;
            return;
        }

    }

    boolean isRequestedToKillFunctionByOneWayAvailableNumberProcess() {
        return killRequestByAvailableProcessFunction;
    }

    boolean isAvailableWayEqualsToZero(int availableWayNumber) {
        return availableWayNumber == 0;
    }

    boolean isNextStepWillBeEqualsToTotalSquareValue() {
        return robot.getStep() == (edgeValue * edgeValue) - 1;
    }


    double calculateWeightResult(int availableWayNumber) {
        return weightOfAvailableWay.getWeightOfDirection()[availableWayNumber];
    }

    boolean isOneWayNumberTooMuchToRunHealtyTheAlgorithm() {
        return oneWayNumbersValue >= 3;
    }

    int calculateDeadlyPoint() {
        return new CalculationDeadlyPoint(game).calculateDeadlyPoint(oneWayNumbersValue);
    }

    void addNavigationToRoadMemoryList() {

        if (oneWayNumbersValue >= 1) {
            navigationService.addNavigationToRoadMemoryList(navigation, (Robot) game.getPlayer());
        }
    }

}
