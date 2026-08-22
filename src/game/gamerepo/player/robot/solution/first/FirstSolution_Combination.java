package game.gamerepo.player.robot.solution.first;

import errormessage.joptionpanel.ShowPanel;
import game.Game;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.location.DirectionLocation;
import game.location.LocationsList;
import game.move.fundamental.MoveBack;
import game.move.fundamental.MoveForward;

import java.util.ArrayList;

public class FirstSolution_Combination extends BaseSolution {

    public FirstSolution_Combination(Game game) {
        super(game);
    }

    @Override
    public int getLocationInput(Game game) {
        prepareation();
        ArrayList<DirectionLocation> locationsList =
                new LocationsList().getListOfLocationsAccordingToPlayerCompass
                        (game.getPlayer().getCompass());




        for (int i = 0; i < locationsList.size() - 1; i++) {

            if (squareProcess.isSquareAvailableToMoveOnIt(game, playerLocation, locationsList.get(i))) {
                return locationsList.get(i).getId();
            }
        }

//        }
        return locationsList.get(locationsList.size() - 1).getId(); //LastLocation

    }

    @Override
    public void buildRobotMove() {
        setMoveForward(new MoveForward(getGame()));
        setMoveBack(new MoveBack(getGame()));
    }

    @Override
    public Integer getSolutionCreatedOrder() {
        return 1;
    }

}
