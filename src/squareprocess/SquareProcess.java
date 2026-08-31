package squareprocess;

import check.CheckSquare;
import game.Game;
import game.location.DirectionLocation;
import game.location.Location;

public class SquareProcess {
    CheckSquare checkSquare = new CheckSquare();

    public boolean isSquareAvailableToMoveOnIt(Game game, Location currentProcessLocation, DirectionLocation directionLocation) {

        return isAreaAvailableToVisit(game, currentProcessLocation, directionLocation)
                && isDirectionAvailableToVisit(game, currentProcessLocation, directionLocation);
    }

    public boolean isAreaAvailableToVisit(Game game, Location playerLocation, DirectionLocation location) {
        location.setCompass(game.getPlayer().getCompass());
        return checkSquare.isSquareFreeFromVisitedArea(game, playerLocation, location.getId());
    }

    public boolean isDirectionAvailableToVisit(Game game, Location currentProcessLocation, DirectionLocation location) {
        return checkSquare.isSquareFreeFromVisitedDirection(game, currentProcessLocation, location.getId());
    }

}
