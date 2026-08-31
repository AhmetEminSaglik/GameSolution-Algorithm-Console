package game.gamerepo.updategamemodel;

import game.Game;
import game.location.DirectionLocation;
import game.move.seal.Signature;
import validation.Validation;

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
