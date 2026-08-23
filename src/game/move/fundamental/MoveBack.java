package game.move.fundamental;

import errormessage.joptionpanel.ShowPanel;
import game.Game;
import game.gamerepo.GameModelProcess;
import game.gamerepo.updategamemodel.UpdateForMovedBack;
import game.location.DirectionLocation;
import game.location.LocationsList;
import game.move.Move;

import java.util.ArrayList;

public class MoveBack extends Move {

    public MoveBack(Game game) {
        super(game);
        updateValuesInGameModel = new UpdateForMovedBack(game);
    }

    @Override
    public void updateVisitedDirection() {
        if (game.getPlayer().getStep() == Math.pow(game.getModel().getGameSquares().length,2)) {
            game.getPlayer().getScore().unlockCounterOfMovingBackLose();
            System.out.println("geri adim atma kilidi acildi");
        }

       if (game.getPlayer().getScore().isLockedCounterOfMovingBackLose() == true) {
//            printGamelastStuation(game);
//            ShowPanel.show(getClass(),"KITLI OLDUGU HALDE GERI ADIM ATTI   Step : "+game.getPlayer().getStep());
            game.getPlayer().getScore().increaseCounterOfDummyBackMove();
            game.getPlayer().getScore().unlockCounterOfMovingBackLose();

        }
        /* Ozel RoundCounter : geri adim atmaya baslandiktan sonra, eger ileri adim atilirsa  ve oyun bitmeden tekrardan geri adim atilmaya baslanirsa o zaman bu deger
        artmaya baslayacak. Amac Cozum bulamadan atilan geri adim sayisini hesaplayip verim oranini bulmak
        * */
    /*    if (game.getPlayer().getScore().getCounterOfMovingBackLose() > 0)
            ShowPanel.show(getClass(), "Geri adim atilacak tikanmayi incele step : " + game.getPlayer().getStep());
        printGamelastStuation(game);*/
        clearAllDirectionBeforeGoBack();
    }

    void clearAllDirectionBeforeGoBack() {
        ArrayList<DirectionLocation> directionLocationList = new LocationsList().getListOfLocationsAccordingToPlayerCompass(game.getPlayer().getCompass());

        for (int i = 0; i < directionLocationList.size(); i++) {
            updateValuesInGameModel.updateValueVisitedDirection(directionLocationList.get(i));
        }
    }

    @Override
    public void updateBeforeStep() {
//        ShowPanel.show(getClass()," Move Back udpateBefore Step");

        updateVisitedArea();
        updateVisitedDirection();
        removeMaxStepBeforeGoingLastStep();
    }

    @Override
    public void updateAfterStep() {
        updatePlayerLocation();
    }

    void removeMaxStepBeforeGoingLastStep() {
        new GameModelProcess(game).deleteMaxStep(game.getPlayer().getStep());
    }

    /*void printGamelastStuation(Game game) {

        String textWillAppendToFile = " Finished totalGame : " + game.getPlayer().getScore().getTotalGameFinishedScore() + "\n";
        textWillAppendToFile += "RoundCounter : " + game.getRoundCounter() + '\n' + "" +
                "Counter of moving back " +game.getPlayer().getScore().getCounterOfMovingBackLose()+"\n"+
                "Step : " + game.getPlayer().getStep() + "\n";


        textWillAppendToFile += new StringFormat().getStringFormatArray(game.getModel().getGameSquares());//  print game squares
        System.out.println(textWillAppendToFile);
        System.out.println();
//        printToFile(textWillAppendToFile);
    }*/

    @Override
    public String toString() {
        return "MoveBack{}";
    }
}
