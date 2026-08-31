package game.gameover;

import check.CheckSquare;
import game.Game;
import game.location.Location;


public class RobotGameOver implements IGameOver {

    Game game;
    int rowCount;
    int colCount;
    CheckSquare checkSquare= new CheckSquare();

    public RobotGameOver(Game game) {
        this.game = game;
        rowCount = game.getModel().getRowCount();
        colCount = game.getModel().getColCount();
    }

    @Override
    public boolean isGameOver(Game game) {
        if (
                isRobotFinishedAllLocations() &&
                allDirectionsAreVisitedAtStep1()) {
            System.out.println("All Solutions are DONE  ::::\n");
            return true;
        }
        return false;
    }


    boolean isRobotFinishedAllLocations() {
        Location robotLocation = game.getPlayer().getLocation();
        return robotLocation.getX() == rowCount - 1 &&
                robotLocation.getY() == colCount - 1;
    }

    boolean allDirectionsAreVisitedAtStep1() {
        return game.getPlayer().getStep() == 1
                && !checkSquare.isAnySquareAvailableInVisitedDirection(game, game.getPlayer().getLocation());
    }
}
