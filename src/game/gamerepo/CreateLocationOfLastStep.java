package game.gamerepo;

import game.Game;
import game.location.CreateLocation;
import game.location.DirectionLocation;

public class CreateLocationOfLastStep {

    Game game;

    public CreateLocationOfLastStep(Game game) {
        this.game = game;
    }

    public DirectionLocation createLastStepLocation() {
        GameModelProcess gameModelProcess = new GameModelProcess(game);
        gameModelProcess.calculateIndexOfGivenStepInGameSquareArrays(game.getPlayer().getStep() - 1);


        CreateLocation createLocation = new CreateLocation();
        createLocation.setX(getOppositeValue(game.getPlayer().getLocation().getX(), gameModelProcess.getX()));
        createLocation.setY(getOppositeValue(game.getPlayer().getLocation().getY(), gameModelProcess.getY()));
        return createLocation;

    }

    int getOppositeValue(int a, int b) {
        return -(a - b);

    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

}
