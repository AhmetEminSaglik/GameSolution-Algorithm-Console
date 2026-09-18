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


    /**
     * Kare simetrisi optimizasyonu: baslangic kareleri artik TUM tahtayi degil,
     * sadece "temel bolgeyi" (0 <= y <= x <= half) geziyor (bkz.
     * Move.changeStartLocationSpecialMovement). Bu bolgenin SON karesi (half, half) -
     * (rowCount-1, colCount-1) DEGIL, oyuncu oraya artik hic gitmiyor.
     */
    boolean isRobotFinishedAllLocations() {
        Location robotLocation = game.getPlayer().getLocation();
        int half = (rowCount - 1) / 2;
        return robotLocation.getX() == half &&
                robotLocation.getY() == half;
    }

    boolean allDirectionsAreVisitedAtStep1() {
        return game.getPlayer().getStep() == 1
                && !checkSquare.isAnySquareAvailableInVisitedDirection(game, game.getPlayer().getLocation());
    }
}
