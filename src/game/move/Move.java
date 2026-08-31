package game.move;

import errormessage.ErrorMessage;
import game.gamerepo.FillGameSquare;
import game.gamerepo.updategamemodel.UpdateValuesInGameModel;
import compass.Compass;
import game.Game;
import game.location.DirectionLocation;
import game.move.fundamental.MoveBack;
import game.play.SelectFirstSqaureToStart;
import print.EasylyReadNumber;
import validation.Validation;


public abstract class Move implements IMove { // ICalculateMove

    public Game game;
    Compass compass;
    Validation validation = new Validation();
    public UpdateValuesInGameModel updateValuesInGameModel;
    private FillGameSquare fillGameSquare;

    DirectionLocation directionLocation;
    int squareEdge;

    public Move(Game game) {
        this.game = game;
        compass = game.getPlayer().getCompass();
        fillGameSquare = new FillGameSquare(game);
        squareEdge = game.getModel().getGameSquares().length;
    }

    @Override
    public boolean isRequiredToChangeStartLocation() {
        return game.getPlayer().getStep() == 1 && getClass().equals(MoveBack.class);
    }

    public final void move(DirectionLocation directionLocation) {
        prepareAllStuff();

        if (isRequiredToChangeStartLocation()) {
            changeStartLocationSpecialMovement();
        } else {
            setLocation(directionLocation);
            updateBeforeStep();
            updatePlayerStepValue();
            updateAfterStep();
            fillGameSquare.printStepInGameSquare();
        }

        if (game.getPlayer().getGameRule().isGameOver(game)) {
            appendFileSquareTotalSolvedValue();
        }
    }

    @Override
    public void updatePlayerStepValue() {
        updateValuesInGameModel.updatePlayerStepValue();
    }

    @Override
    public void changeStartLocationSpecialMovement() {
        appendFileSquareTotalSolvedValue();
        int locationX = game.getPlayer().getLocation().getX();
        int locationY = game.getPlayer().getLocation().getY();

        locationX++;
        if (locationX >= game.getModel().getGameSquares().length) {
            locationX = 0;
            locationY++;

        }


        if (locationY < game.getModel().getGameSquares().length) {

            try {

                ResetAllDataForGameAndPlayer resetData = new ResetAllDataForGameAndPlayer(game);
                resetData.clearPlayerData(game.getPlayer());
                resetData.clearGameData(game);
                SelectFirstSqaureToStart selectFirstSqaureToStart = new SelectFirstSqaureToStart(game);
                selectFirstSqaureToStart.selectSquareStart(locationX, locationY);

                selectFirstSqaureToStart.locateThePlayer();
            } catch (InterruptedException e) {
                ErrorMessage.appearFatalError(getClass(), "OYUN SIFIRLAMASINDA SORUN OLDU : " + e.getMessage());

            }
        } else {
            ErrorMessage.appearWarnings(getClass(), "Y siniri asti - baslangic karesi daha fazla ilerletilemedi");
        }
    }

    void appendFileSquareTotalSolvedValue() {
//        todo: burasi, db'ye kaydedilecek kaldirildi.

        int locationX = game.getPlayer().getLocation().getX();
        int locationY = game.getPlayer().getLocation().getY();
        int squareTotalSolvedValue = game.getPlayer().getSquareTotalSolvedValue();

        String scoreValue = new EasylyReadNumber().getReadableNumberInStringFormat(squareTotalSolvedValue);

//        game.getPlayer().getPrintableFileScore().append(scoreValue);


        String text = "[" + locationX + "]" + "[" + locationY + "] = " + scoreValue + "\n";
        if (locationX == squareEdge - 1) {
            text += "\n";
        }

        game.getPlayer().getPrintableFileTotalScoreCount().append(text);
        game.getPlayer().resetSquareTotalSolvedValue();

    }


    @Override
    public void prepareAllStuff() {

    }

    @Override
    public void updateVisitedArea() {
        updateValuesInGameModel.updateValueVisitedArea();
    }

    @Override
    public void updatePlayerLocation() {
        updateValuesInGameModel.changePlayerLocation(getDirectionLocation());
    }

    public DirectionLocation getDirectionLocation() {
        return directionLocation;
    }

    public void setLocation(DirectionLocation directionLocation) {
        this.directionLocation = directionLocation;
    }

    @Override
    public String toString() {
        return "Move{" + "game=" + game + ", compass=" + compass + ", validation=" + validation + ", updateValuesInGameModel=" + updateValuesInGameModel + ", fillGameSquare=" + fillGameSquare + ", directionLocation=" + directionLocation + '}';
    }

    public FillGameSquare getFillGameSquare() {
        return fillGameSquare;
    }
}
