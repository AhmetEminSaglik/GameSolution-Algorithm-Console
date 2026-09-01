package game.play;

import game.Game;
import game.gamerepo.player.Player;
import game.gamerepo.player.person.Person;
import game.location.DirectionLocation;
import game.move.Move;
import persistence.GridPath;
import persistence.NoOpSolutionSink;
import persistence.SolutionSink;
import persistence.checkpoint.CheckpointRecorder;
import print.EasylyReadNumber;
import print.FileWriteProcess;
import print.PrintAble;
import printarray.StringFormat;

public class PlayGame {

    Game game;
    Player player;
    PrepareGame prepareGame;
    StringFormat stringFormat = new StringFormat();
    TimeCalcuation timeCalcuation;
    private PrintAble printable;

    private final SolutionSink solutionSink;
    private final boolean recordSolutions;
    private final CheckpointRecorder checkpointRecorder;
    private long solutionIndex = 0;
    /** > 0 ise: checkpoint'ten devam. game state disaridan restore edilmis; PrepareGame ATLANIR. */
    private long resumeFromIndex = 0;

    public PlayGame(Game game) {
        this(game, new NoOpSolutionSink());
    }

    public PlayGame(Game game, SolutionSink solutionSink) {
        this(game, solutionSink, CheckpointRecorder.NONE);
    }

    public PlayGame(Game game, SolutionSink solutionSink, CheckpointRecorder checkpointRecorder) {
        this.game = game;
        this.solutionSink = solutionSink;
        this.recordSolutions = solutionSink.isEnabled();
        this.checkpointRecorder = checkpointRecorder;
        player = game.getPlayer();
        printable = new FileWriteProcess(game.getPlayer().getName());
    }

    /**
     * Checkpoint'ten devam modu. Cagrilirsa: game state'inin ZATEN restore edilmis
     * oldugu varsayilir (bkz. Algo2ResumeService), PrepareGame calistirilmaz ve
     * solutionIndex buradan devam eder.
     */
    public void resumeFrom(long solutionIndex) {
        this.resumeFromIndex = solutionIndex;
    }


    public void playGame() {
        player.startTimeKeeper();
        solutionSink.beginRun(new SolutionSink.RunInfo(
                game.getModel().getRowCount(), game.getModel().getColCount(), player.getSolutionName()));

        if (resumeFromIndex > 0) {
            this.solutionIndex = resumeFromIndex;
            System.out.println("Checkpoint'ten devam: #" + resumeFromIndex);
        } else {
            prepareGame = new PrepareGame(game);
        }
        Move moveForwardOrBack;

        printTableIfPersonPlays();
        appendFileSolutionName();

        int prevStep = player.getStep();
        if (recordSolutions) {
            solutionSink.onRoot(player.getLocation().getX(), player.getLocation().getY());
        }

        while (!player.getGameRule().isGameOver(game)) {

            game.increaseRoundCounter();
            int choose = player.getInput(game);
            moveForwardOrBack = getMoveBackOrForward(choose);
            moveForwardOrBack.move(
                    new DirectionLocation().
                            getLocationValueAccordingToEnteredValue(game, choose));

            if (recordSolutions) {
                emitMoveEvent(prevStep, choose);
            }
            prevStep = player.getStep();

            calculatePlayerTotalWinScore();

//            if (game.getModel().getGameSquares()[0][0] != 1)
//                break;
//            if (player.isPrintableStepSituation() == true) {
//                printGamelastStuation(game);
//            }

            printTableIfPersonPlays();

//            printGamelastStuation(game);
//            if(player.getStep()==25){
//            }

        }

        solutionSink.endRun(new SolutionSink.RunResult(
                player.getScore().getTotalGameFinishedScore(),
                game.getRoundCounter(),
                player.getScore().getCounterTotalBackStep(),
                player.getScore().getCounterOfDummyBackMove()));

        System.out.println("Total Number Solved: " + getEasyReadyNumber(player.getScore().getTotalGameFinishedScore()));
        saveGameResultToScore();
    }


    void appendFileSquareTotalSolvedValue() {

        int locationX = game.getPlayer().getLocation().getX();
        int locationY = game.getPlayer().getLocation().getY();
        int squareTotalSolvedValue = game.getPlayer().getSquareTotalSolvedValue();


        String scoreValue = new EasylyReadNumber().getReadableNumberInStringFormat(squareTotalSolvedValue);

        game.getPlayer().getPrintableFileScore().append(scoreValue);


        String text = "[" + locationX + "]" + "[" + locationY + "] = " + scoreValue + "\n";

        game.getPlayer().getPrintableFileScore().append(text);
        game.getPlayer().resetSquareTotalSolvedValue();

    }

    Move getMoveBackOrForward(int index) {
        if (index == player.getCompass().getLastLocation()) {
            return player.getPlayerMove().getMoveBack();
        }
        return player.getPlayerMove().getMoveForward();
    }

    void calculatePlayerTotalWinScore() {
        if (player.getStep() == game.getModel().getTotalSquareCount()) {
            player.getScore().increaseTotalGameFinishedScore();
//            System.out.println("Total Solved : " + player.getScore().getTotalGameFinishedScore());
//            printGamelastStuation(game);
            player.increaseSquareTotalSolvedValue();
            solutionIndex++;
            checkpointRecorder.maybeRecord(game, solutionIndex);
            if (recordSolutions) {
                solutionSink.accept(new SolutionSink.FoundSolution(solutionIndex, extractCurrentPath()));
            }
        }
    }

    /**
     * Bir hamleden sonra sink'e ileri/geri/yeni-kok olayini bildirir.
     * {@code prevStep} hamleden onceki adim numarasi, {@code choose} secilen yon.
     */
    private void emitMoveEvent(int prevStep, int choose) {
        int curStep = player.getStep();
        if (curStep > prevStep) {
            solutionSink.onForward(curStep, player.getLocation().getX(), player.getLocation().getY(), choose);
        } else if (curStep < prevStep) {
            solutionSink.onBackward(prevStep - curStep);
        } else {
            // adim 1'de kaldi -> baslangic karesi degisti (changeStartLocationSpecialMovement)
            solutionSink.onRoot(player.getLocation().getX(), player.getLocation().getY());
        }
    }

    /**
     * Tahtadaki adim numaralarindan ({@code gameSquares[x][y] = k}) o anki tam
     * cozum yolunu cikarir. Tahta bu noktada tamamen dolu (1..N).
     */
    private GridPath extractCurrentPath() {
        int rows = game.getModel().getRowCount();
        int cols = game.getModel().getColCount();
        int[][] board = game.getModel().getGameSquares();
        int[][] cells = new int[rows * cols][2];
        for (int x = 0; x < rows; x++) {
            for (int y = 0; y < cols; y++) {
                int step = board[x][y];
                cells[step - 1][0] = x;
                cells[step - 1][1] = y;
            }
        }
        return new GridPath(rows, cols, cells[0][0], cells[0][1], cells);
    }


    void saveGameResultToScore() {
        timeCalcuation = new TimeCalcuation();
        player.getScore().updatePlayedTime();
        System.out.println("Elapsed time : " + timeCalcuation.getTotalPassedTime(player));
        System.out.println("Total Back Step : " + getEasyReadyNumber(game.getPlayer().getScore().getCounterTotalBackStep()));
        System.out.println("Total Step : " + getEasyReadyNumber(game.getRoundCounter()));
        System.out.println("Total Dummy Back Step)  : " + getEasyReadyNumber(game.getPlayer().getScore().getCounterOfDummyBackMove()));
        appendFileTotalSolvedValue();
    }

    void appendFileTotalSolvedValue() {


        long totalFinishedScore = player.getScore().getTotalGameFinishedScore();
        String scoreValue = getEasyReadyNumber(totalFinishedScore);

        String text = "--------------";
        text += "\nSolution :" + player.getSolutionName();
        text += "\nTotal played time :" + timeCalcuation.getTotalPassedTime(player);
        text += "\ntotal Solved : " + scoreValue;
        if (game.getPlayer().getScore().getOverLongTotalGameFinishedScore() > 0) {
            text += "\nOverLong Solved " + game.getPlayer().getScore().getOverLongTotalGameFinishedScore() + "   (this means that==> "
                    + game.getOverLongRoundCounter() + " * " + Long.MAX_VALUE + " + " + game.getRoundCounter() + ")";
        }

        text += "\nRound Counter (While loop)  : " + getEasyReadyNumber(game.getRoundCounter());
        text += "\nTotal Dummy Step  : " + getEasyReadyNumber(game.getPlayer().getScore().getCounterOfDummyBackMove());
        if (game.getOverLongRoundCounter() > 0) {
            text += "OverLongRoundCounter : " + game.getOverLongRoundCounter() + "   (this means that==> "
                    + game.getOverLongRoundCounter() + " * " + Long.MAX_VALUE + " + " + game.getRoundCounter() + ")";
        }


        text += "\n\n ========================================================== \n\n\n";
        game.getPlayer().getPrintableFileScore().append(text);

    }

    String getEasyReadyNumber(long number) {
        return new EasylyReadNumber().getReadableNumberInStringFormat(number);
    }

    void appendFileSolutionName() {
//        String text = ">>>>>>>>>>>>>>  " + ((Robot) player).getSolution().getClass().getSimpleName() + " : \n\n";
//        game.getPlayer().getPrintableFileScore().append(text);

    }


    void printGamelastStuation(Game game) {// todo: burasi printe ediliyordu. db'ye save edilecek. loglama icin burasi tekrar aktif edilebilir.
        String textWillAppendToFile = "Finished totalGame : " + getEasyReadyNumber(player.getScore().getTotalGameFinishedScore()) + "\n";
        textWillAppendToFile += "Total Step : " + getEasyReadyNumber(game.getRoundCounter()) + '\n' + "" +
                "Total Back Step : " + getEasyReadyNumber(game.getPlayer().getScore().getCounterTotalBackStep()) +
                "\nTotal Dummy Back Step : " + getEasyReadyNumber(game.getPlayer().getScore().getCounterOfDummyBackMove())+
                "\nStep : " + player.getStep() + "\n";


//        textWillAppendToFile += stringFormat.getStringFormatArray(game.getModel().getGameSquares());//  print game squares
//        System.out.println(textWillAppendToFile);
//        System.out.println();
//        printToFile(textWillAppendToFile);
    }

    void printToFile(String text) {
//        game.getPlayer().getPrintableFileScore().append(text);
//        printable.append(text);
    }

    void printTableIfPersonPlays(){
        if(player instanceof Person){
            printGamelastStuation(game);
        }
    }
}
