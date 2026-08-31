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
                    "Harita en az " + Validation.MIN_MAP_SIZE + "x" + Validation.MIN_MAP_SIZE
                            + " olmali (girilen: " + verticalSquares + " x " + horizontalSquares + ")");
        }

    }

}
