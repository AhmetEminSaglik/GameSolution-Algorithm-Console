package game.gameover;

import check.CheckSquare;
import game.Game;
import game.location.Location;
import persistence.RunMapType;


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


    /**
     * Son baslangic karesi tarama tipine gore (bkz. Move.changeStartLocationSpecialMovement):
     * UNIQUE sadece "temel bolgeyi" (0 <= y <= x <= half) gezer, son karesi (half, half);
     * ALL tum tahtayi gezer, son karesi (rowCount-1, colCount-1).
     */
    boolean isRobotFinishedAllLocations() {
        Location robotLocation = game.getPlayer().getLocation();
        if (RunMapType.selected() == RunMapType.ALL) {
            return robotLocation.getX() == rowCount - 1 &&
                    robotLocation.getY() == colCount - 1;
        }
        int half = (rowCount - 1) / 2;
        return robotLocation.getX() == half &&
                robotLocation.getY() == half;
    }

    boolean allDirectionsAreVisitedAtStep1() {
        return game.getPlayer().getStep() == 1
                && !checkSquare.isAnySquareAvailableInVisitedDirection(game, game.getPlayer().getLocation());
    }
}
