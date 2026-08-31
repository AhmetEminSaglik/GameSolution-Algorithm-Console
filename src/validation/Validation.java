package validation;

import compass.Compass;
import errormessage.ErrorMessage;
import game.Game;
import game.location.DirectionLocation;
import game.location.Location;

public class Validation {

    /** Gecerli en kucuk kenar 5'tir: kenar bu degerden (kesinlikle) buyuk olmali. */
    public static final int MIN_SQUARE_EDGE = 4;

    private Compass compass;

    public boolean validateSquareNumbers(int number) {
        return number > MIN_SQUARE_EDGE;
    }

    /**
     * if value is positive return true else return false
     */
    boolean needToCalculateBySum(int value) {
        return value >= 0;
    }

    /**
     * Instead of coding to calculate both X and Y, one code and calculate
     * player.X and direction. X or Y. It is belongs to which direction you send
     */
    boolean calculateValidOrNot(int maxSquare, int location, int value) {
        if (needToCalculateBySum(value)) {
            return location + value < maxSquare;
        }
        return location + value >= 0;
    }

    /**
     * @param input = Keyboards input direction value
     */
    public boolean isInputValidForArray(Game game,Location currentProcessLocation, int input) {
        compass = game.getPlayer().getCompass();
        Location location = new DirectionLocation().getLocationFromCompass(compass, input);

        /* kuzeyden baslayip saat yonun`de kontrol edecegi icin  yon pusulasini gonderiyoruz
        Kullanici pusulasi  kullanici girisli pusulada kullanildigi icin burada dizi indexinde kullanamiyoruz
        O yuzden bu sekilde gonderdik
         */
        try {
            return calculateValidOrNot(game.getModel().getGameSquares().length, currentProcessLocation.getX(), location.getX())
                    && calculateValidOrNot(game.getModel().getGameSquares().length, currentProcessLocation.getY(), location.getY());
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
