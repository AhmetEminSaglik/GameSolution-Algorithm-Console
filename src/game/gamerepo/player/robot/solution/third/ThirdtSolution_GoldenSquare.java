package game.gamerepo.player.robot.solution.third;

import errormessage.joptionpanel.ShowPanel;
import game.Game;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.move.fundamental.secondsolutionforrobot.MoveBackSecondSolution;
import game.move.fundamental.secondsolutionforrobot.MoveForwardSecondSolution;
import game.move.fundamental.thirdsolution.MoveBackThirdSolution;
import game.move.fundamental.thirdsolution.MoveForwardThirdSolution;

public class ThirdtSolution_GoldenSquare extends BaseSolution {
    public ThirdtSolution_GoldenSquare(Game game) {
        super(game);
    }



    @Override
    public int getLocationInput(Game game) {
        prepareation();

        MathFunctionWithSpecialFeaturesForThirdSolution mathFunctionWithSpecialFeaturesForThirdSolution
                = new MathFunctionWithSpecialFeaturesForThirdSolution(game, playerLocation);

        return mathFunctionWithSpecialFeaturesForThirdSolution.calculateFunctionResult();
    }

    @Override
    public void buildRobotMove() {
        setMoveForward(new MoveForwardThirdSolution(getGame()));
        setMoveBack(new MoveBackThirdSolution(getGame()));
    }

    @Override
    public Integer getSolutionCreatedOrder() {
        return 3;
    }
}
