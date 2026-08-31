package game.gamerepo.player.robot.solution;

import check.CheckSquare;
import game.Game;
import game.location.Location;
import game.move.fundamental.MoveBack;
import game.move.fundamental.MoveForward;
import squareprocess.SquareProcess;

public abstract class BaseSolution {
    private Game game;
    protected Location playerLocation;

    public BaseSolution(Game game) {
        this.game = game;

    }

    private MoveForward moveForward;
    private MoveBack moveBack;
    public CheckSquare checkSquare = new CheckSquare();
    public SquareProcess squareProcess = new SquareProcess();

    public void prepareation() {
        playerLocation = checkSquare.getPlayerLocation(getGame());
    }

    public abstract int getLocationInput(Game game);//, RobotMemory robotMemory

    public MoveForward getMoveForward() {
        return moveForward;
    }

    public void setMoveForward(MoveForward moveForward) {
        this.moveForward = moveForward;
    }

    public MoveBack getMoveBack() {
        return moveBack;
    }

    public void setMoveBack(MoveBack moveBack) {
        this.moveBack = moveBack;
    }

    public Game getGame() {
        return game;
    }

    public abstract void buildRobotMove();

    public final String getSolutionFileName() {
        return "Solution-" + getSolutionCreatedOrder()+"-"+game.getModel().getGameMapAreaName();
    }

    public abstract Integer getSolutionCreatedOrder();
}
