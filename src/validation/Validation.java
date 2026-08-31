package validation;

import game.gamerepo.player.Player;
import compass.Compass;
import errormessage.ErrorMessage;
import game.Game;
import game.location.DirectionLocation;
import game.location.Location;

public class Validation {

    private Compass compass;

    public boolean validateSquareNumbers(int number) {

        final int minimum = 4;

        if (number > minimum) {
            return true;
        }

        return false;
    }

    /**
     * if value is positive return true else return false
     */
    boolean needToCalculateBySum(int value) {

        if (value >= 0) {
            return true;
        }
        return false;
    }

    /**
     * Instead of coding to calculate both X and Y, one code and calculate
     * player.X and direction. X or Y. It is belongs to which direction you send
     */
    boolean calculateValidOrNot(int maxSquare, int location, int value) {
        if (needToCalculateBySum(value)) {
            if (location + value < maxSquare) {
                return true;
            }
        } else {
            if (location + value >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param input = Keyboards input direction value
     */
    public boolean isInputValidForArray(Game game,Location currentProcessLocation, int input) {
        Location location;
        Player player = game.getPlayer();
        compass = game.getPlayer().getCompass();
        location = new DirectionLocation().getLocationFromCompass(compass, input);

        /* kuzeyden baslayip saat yonun`de kontrol edecegi icin  yon pusulasini gonderiyoruz
        Kullanici pusulasi  kullanici girisli pusulada kullanildigi icin burada dizi indexinde kullanamiyoruz
        O yuzden bu sekilde gonderdik
         */
        try {

            // ??? HATA CIKARSA BUNU AKTIFLESTIR  switchDirection = new SwitchDirection(compass);
            // switchDirection.choseDirection(input);
            if (calculateValidOrNot(game.getModel().getGameSquares().length, currentProcessLocation.getX(), location.getX())
                    && calculateValidOrNot(game.getModel().getGameSquares().length, currentProcessLocation.getY(), location.getY())) {

                return true;
            }
        } catch (Exception ex) {
            ErrorMessage.appearClassicError(getClass(), ex.getMessage());
        }
        return false;

    }

    public boolean isStepValueAvailable(Game game, int step) {
        return step < game.getModel().getTotalSquareCount();
    }

    public Compass getCompass() {
        return compass;
    }

    public void setCompass(Compass compass) {
        this.compass = compass;
    }
}
