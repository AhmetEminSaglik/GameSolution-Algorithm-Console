package game.gamerepo.updategamemodel;

import errormessage.joptionpanel.ShowPanel;
import game.Game;
import game.location.DirectionLocation;
import game.move.seal.Signature;
import sleep.Sleep;
import validation.Validation;

import javax.swing.*;

public class UpdateForMovedBack extends UpdateValuesInGameModel {

    public UpdateForMovedBack(Game game) {
        super(game);
    }

    @Override
    public void updatePlayerStepValue() {
        game.getPlayer().decreaseStep();
        game.getPlayer().getScore().increaseCounterTotalBackStep();
    }

    @Override
    Signature ifMovedForwardThenSealTheLocation() {
        return Signature.UNSEAL;
    }

    @Override
    public void updateValueVisitedDirection(DirectionLocation directionLocation) {
        int step = player.getStep();
        if (new Validation().isStepValueAvailable(game, step)) {
            player.updateVisitedDirection(ifMovedForwardThenSealTheLocation().isSealed(), step, directionLocation);
        }
    }


}
