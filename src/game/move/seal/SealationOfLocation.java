package game.move.seal;

import errormessage.ErrorMessage;
import game.Game;
import game.gamerepo.player.Player;

public class SealationOfLocation implements UpdateableLocation {
    Game game;
    Player player;

    public SealationOfLocation(Game game) {
        this.game = game;
        player = game.getPlayer();
    }

    @Override
    public void updateLocationCondition(boolean[][] area, Signature signature) {
        try {
            area[game.getPlayer().getLocation().getX()][game.getPlayer().getLocation().getY()] = signature.isSealed();

        } catch (ArrayIndexOutOfBoundsException ex) {
            ErrorMessage.appearWarnings(getClass(), "Sealation index disari tasti"
                    + " x=" + game.getPlayer().getLocation().getX()
                    + " y=" + game.getPlayer().getLocation().getY()
                    + " step=" + game.getPlayer().getStep()
                    + " : " + ex.getMessage());
        }

    }
}
