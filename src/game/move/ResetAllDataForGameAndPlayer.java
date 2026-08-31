package game.move;

import game.Game;
import game.gamerepo.BuildGame;
import game.gamerepo.player.Player;

public class ResetAllDataForGameAndPlayer {
    int rowCount;
    int colCount;
    BuildGame buildGame;

    public ResetAllDataForGameAndPlayer(Game game) throws InterruptedException {
        rowCount = game.getModel().getRowCount();
        colCount = game.getModel().getColCount();
        buildGame = new BuildGame(rowCount, colCount);
    }


    public void clearGameData(Game game) throws InterruptedException {
        game.getModel().setGameSquares(new int[rowCount][colCount]);
        game.getModel().setVisitedAreas(new boolean[rowCount][colCount]);
    }

    public void clearPlayerData(Player player) {
        player.clearVisitedDirections();
        player.clearStepValue();
    }

}
