package game.move.fundamental;

import game.Game;
import game.gamerepo.updategamemodel.UpdateForMovedForward;
import game.move.Move;
import trace.Trace;

public class MoveForward extends Move {

    public MoveForward(Game game) {
        super(game);
        updateValuesInGameModel = new UpdateForMovedForward(game);
    }

    @Override
    public void updateVisitedDirection() {
        if (Trace.ENABLED) Trace.log("forward", "step=" + game.getPlayer().getStep()
                + " dir=" + getDirectionLocation().getId());
        game.getPlayer().getScore().lockCounterOfMovingBackLose();
        updateValuesInGameModel.updateValueVisitedDirection(getDirectionLocation());
    }

    @Override
    public void updateBeforeStep() {
    }

    @Override
    public void updateAfterStep() {
        updatePlayerLocation();
        updateVisitedArea();
        updateVisitedDirection();
    }

    @Override
    public String toString() {
        return "MoveForward{}";
    }
}
