package game.play.input.person;

import check.CheckSquare;
import compass.Compass;
import compass.KeyboardCompass;
import errormessage.ErrorMessage;
import game.Game;
import game.location.Location;
import game.play.input.BaseControlInput;
import validation.Validation;


public class PersonInput extends BaseControlInput {

    CheckSquare checkSquare = new CheckSquare();
    private SafeScannerInput scannerInput = new SafeScannerInput();

    public PersonInput(Game game) {
        super(game);
    }


    @Override
    public int getInput() {
        while (true) {
            int choose = scannerInput.getInput();
            if (isMoveableDirectionInput(choose)) {
                return choose;
            } else {

                ErrorMessage.appearClassicError(getClass(),"You can not  go that direction, please choose another direction\n" +
                        "chose : "+choose+"\n" +
                        "checkSquare.getCompass().getNorth() " +checkSquare.getCompass().getNorth());
            }
        }
    }

//    @Override
    public boolean isInputSuitableToMoveForward(Game game, int choose) {
        return checkInputForForward(game, choose);
    }

    boolean checkInputForForward(Game game, int choose) {
        Validation validation = new Validation();
        Compass compass = new KeyboardCompass();

        validation.setCompass(compass);

        checkSquare.setCompass(compass);

        return validation.isInputValidForArray(game, game.getPlayer().getLocation(), choose)
                && checkSquare.isSquareFreeFromVisitedArea(game, getLocationToCheck(game), choose);
    }

    Location getLocationToCheck(Game game) {
        return checkSquare.getPlayerLocation(game);
    }

//    @Override
    public boolean isInputSuitableToMoveBack(Game game, int choose) {
        return checkInputForBack(game, choose);
    }

    boolean checkInputForBack(Game game, int choose) {
        return choose == new KeyboardCompass().getLastLocation() && game.getPlayer().getStep() > 1;
    }

    public boolean isMoveableDirectionInput(int choose) {
        return isInputSuitableToMoveForward(game, choose) || isInputSuitableToMoveBack(game, choose);
    }
}
