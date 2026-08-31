package game.play.input.robot;

import game.Game;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.play.input.BaseControlInput;

public class RobotInput extends BaseControlInput {

    BaseSolution solution;

    public RobotInput(BaseSolution solution, Game game) {
        super(game);
        this.solution = solution;
    }

    @Override
    public int getInput() {
        return solution.getLocationInput(game);
    }

}
