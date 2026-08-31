package validation;

import errormessage.InvalidGameConfigException;

public class SquareValidationGame {

    int horizontalSquares;
    int verticalSquares;

    public SquareValidationGame(int verticalSquares, int horizontalSquares) throws InvalidGameConfigException {

        if (new Validation().validateSquareNumbers(verticalSquares) && new Validation().validateSquareNumbers(horizontalSquares)) {
            this.verticalSquares = verticalSquares;
            this.horizontalSquares = horizontalSquares;
        } else {
            throw new InvalidGameConfigException(
                    "Kenar sayisi " + Validation.MIN_SQUARE_EDGE + "'ten buyuk olmali (girilen: "
                            + verticalSquares + " x " + horizontalSquares + ")");
        }

    }

}
