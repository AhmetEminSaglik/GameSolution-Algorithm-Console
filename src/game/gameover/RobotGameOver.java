package game.gameover;

import check.CheckSquare;
import errormessage.joptionpanel.ShowPanel;
import game.Game;
import game.location.Location;


public class RobotGameOver implements IGameOver {

    Game game;
    int squareEdge;
    CheckSquare checkSquare= new CheckSquare();

    public RobotGameOver(Game game) {
        this.game = game;
        squareEdge = game.getModel().getGameSquares().length;
    }

    @Override
    public boolean isGameOver(Game game) {
        if (
                isRobotFinishedAllLocations() &&
//                !isRobotFinishedFirstSquare()&&
                allDirectionsAreVisitedAtStep1()) {
            System.out.println("All Solutions are DONE  ::::\n");
//            System.out.println("All Solutions are DONE For First Square (1/" + game.getModel().getGameSquares().length + ") ::::\n");
            return true;
        }
        return false;
    }


    boolean isRobotFinishedFirstSquare() {

        Location robotLocation = game.getPlayer().getLocation();
        if (robotLocation.getX() == 0 && robotLocation.getY() == 0) {
            return false;
        }
        return true;

    }

    boolean isRobotFinishedAllLocations() {
        Location robotLocation = game.getPlayer().getLocation();

        if (robotLocation.getX() == squareEdge - 1 &&
                robotLocation.getY() == squareEdge - 1) {
//            ShowPanel.show(getClass(), "GEldu buraya x :" + robotLocation.getX() + " - Y : " + robotLocation.getY());

            return true;
        }
        return false;

    }

    boolean allDirectionsAreVisitedAtStep1() {
        if (game.getPlayer().getStep() == 1) {

            return !(checkSquare.isAnySquareAvailableInVisitedDirection(game, game.getPlayer().getLocation()));
        }
        return false;
    }
}
