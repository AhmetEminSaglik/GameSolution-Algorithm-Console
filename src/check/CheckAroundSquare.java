package check;

import game.Game;
import game.location.DirectionLocation;

import java.util.ArrayList;

import game.location.Location;
import game.location.LocationsList;

public class CheckAroundSquare extends BaseCheck {

    Game game;
    CheckSquare checkSquare = new CheckSquare();
    ArrayList<DirectionLocation> locationList;

    public CheckAroundSquare(Game game) {
        this.game = game;
        locationList = new LocationsList().getListOfLocationsAccordingToPlayerCompass(game.getPlayer().getCompass());
    }

    public boolean isThereAnyAvailableSquare() {
        setCompass(game.getPlayer().getCompass());
        checkSquare.setCompass(getCompass());

        for (int i = 0; i < locationList.size(); i++) {
            if (isLocationAvailable(game, locationList.get(i).getId())) {
                return true;
            }
        }
        return false;
    }

    boolean isLocationAvailable(Game game, int directionIndex) {
        Location locationWillBeCheck = getPlayerLocation(game);
        return checkSquare.isSquareFreeFromVisitedArea(game, locationWillBeCheck, directionIndex);
    }

}
