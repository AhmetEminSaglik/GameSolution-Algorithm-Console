package game.play;

import compass.Compass;
import game.Game;

public class PrepareGame {

    Game game;
    Compass compass;
    SelectFirstSqaureToStart selectFirstSqaureToStart;

    public PrepareGame(Game game) {

        fillNullReferans(game);
        prepareToPlay();

    }

    void fillNullReferans(Game game) {
        this.game = game;

        selectFirstSqaureToStart = new SelectFirstSqaureToStart(game);
        compass = game.getPlayer().getCompass();
    }

    void prepareToPlay() {
        selectFirstSqaureToStart.selectSquareStart(0, 0);
        selectFirstSqaureToStart.locateThePlayer();
    }

}
