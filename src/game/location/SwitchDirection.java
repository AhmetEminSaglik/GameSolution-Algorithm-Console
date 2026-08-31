package game.location;

import compass.Compass;
import errormessage.ErrorMessage;
import trace.Trace;
import game.location.direction.South;
import game.location.direction.East;
import game.location.direction.LastLocation;
import game.location.direction.North;
import game.location.direction.West;
import game.location.direction.NorthEast;
import game.location.direction.NorthWest;
import game.location.direction.SouthWest;
import game.location.direction.SouthEast;

public class SwitchDirection {

    Compass compass;

    public SwitchDirection(Compass compass) {
        this.compass = compass;
    }

    /**
     * value, verilen pusulada gecerli bir yon degilse ClasicError loglanir ve
     * {@code null} donulur. Cagiran taraf null'a karsi dikkatli olmali.
     * TODO: yonler bir enum'a alininca bu 9'lu if zinciri ve null sozlesmesi kalkacak.
     */
    public DirectionLocation choseDirection(int value) {
        if (Trace.ENABLED) Trace.log("choseDirection", "value=" + value);

        if (compass.getNorth() == value) {
            North north = new North();
            north.setCompass(compass);
            return north;
        }
        if (compass.getNorthEast() == value) {
            NorthEast northEast = new NorthEast();
            northEast.setCompass(compass);
            return northEast;
        }
        if (compass.getEast() == value) {
            East east = new East();
            east.setCompass(compass);
            return east;
        }
        if (compass.getSouthEast() == value) {
            SouthEast southEast = new SouthEast();
            southEast.setCompass(compass);
            return southEast;
        }
        if (compass.getSouth() == value) {
            South south = new South();
            south.setCompass(compass);
            return south;
        }
        if (compass.getSouthWest() == value) {
            SouthWest southWest = new SouthWest();
            southWest.setCompass(compass);
            return southWest;
        }
        if (compass.getWest() == value) {
            West west = new West();
            west.setCompass(compass);
            return west;
        }
        if (compass.getNorthWest() == value) {
            NorthWest northWest = new NorthWest();
            northWest.setCompass(compass);
            return northWest;
        }
        if (compass.getLastLocation() == value) {
            LastLocation lastLocation = new LastLocation();
            lastLocation.setCompass(compass);
            return lastLocation;
        }

        ErrorMessage.appearClassicError(getClass(), " compass  : " + compass.getClass().getSimpleName() + " -> Unknown Option  : " + value);

        return null;
    }

}
