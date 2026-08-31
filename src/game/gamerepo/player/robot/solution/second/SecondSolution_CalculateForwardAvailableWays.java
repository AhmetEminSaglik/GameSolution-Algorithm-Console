package game.gamerepo.player.robot.solution.second;

import game.Game;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.move.fundamental.secondsolutionforrobot.MoveBackSecondSolution;
import game.move.fundamental.secondsolutionforrobot.MoveForwardSecondSolution;

public class SecondSolution_CalculateForwardAvailableWays extends BaseSolution {
    public SecondSolution_CalculateForwardAvailableWays(Game game) {
        super(game);
    }

    @Override
    public int getLocationInput(Game game) {
        prepareation();

        MathFunctionForSecondSolution mathFunctionForSecondSolution =
                new MathFunctionForSecondSolution(game, playerLocation);
        return mathFunctionForSecondSolution.calculateFunctionResult();
    }

    @Override
    public void buildRobotMove() {
        setMoveForward(new MoveForwardSecondSolution(getGame()));
        setMoveBack(new MoveBackSecondSolution(getGame()));
    }

    @Override
    public Integer getSolutionCreatedOrder() {
        return 2;
    }

    @Override
    public String toString() {
        return "Second Solution  {" + super.toString() + '}';
    }
}
