package check;

import game.Game;
import game.location.DirectionLocation;
import game.location.Location;
import game.location.LocationsList;

import java.util.ArrayList;

public class CheckSquare extends BaseCheck {

    public boolean isSquareFreeFromVisitedArea(Game game, Location currentProcessLocation, int directionIndex) {
        setCompass(game.getPlayer().getCompass());
        if (isIndexsSuitableForArray(game, currentProcessLocation, directionIndex)) {
            Location location = new DirectionLocation().getLocationFromCompass(getCompass(), directionIndex);
            // location, gecersiz bir yon degeri gelirse null olabilir (bkz. SwitchDirection.choseDirection).
            // Normal akista bu deger her zaman gecerli bir pusula yonu; guard sadece gizli NPE'yi engeller.
            if (location != null && !game.getModel().getVisitedAreas()[currentProcessLocation.getX()
                    + location.getX()][currentProcessLocation.getY() + location.getY()]) {
                return true;
            }
        }
        return false;

    }

    public boolean isSquareFreeFromVisitedDirection(Game game, Location currentProcessLocation, int directionIndex) {

        if (isIndexsSuitableForArray(game, currentProcessLocation, directionIndex)) {
            if (game.getPlayer().getVisitedDirections()[game.getPlayer().getStep()][directionIndex] == false) {
                return true;
            }
        }
        return false;
    }

    public boolean isIndexsSuitableForArray(Game game, Location currentProcessLocation, int choose) {
        validation.setCompass(getCompass());
        return validation.isInputValidForArray(game, currentProcessLocation, choose);
    }


    public boolean isAnySquareAvailableInVisitedDirection(Game game, Location currentProcessLocation) {
        ArrayList<DirectionLocation> locationList = new LocationsList().getListOfLocationsAccordingToPlayerCompass(game.getPlayer().getCompass());

        for (int i = 0; i < locationList.size() - 1; i++) {

            if (isSquareFreeFromVisitedDirection(game, currentProcessLocation, locationList.get(i).getId()) == true) {
                return true;
            }

        }
        return false;
    }

}
