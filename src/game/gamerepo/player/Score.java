package game.gamerepo.player;

import game.Game;

public class Score {
    Game game;
    Player player;
    String playedTime;
    /* Geri adim atilmasi beklenmeyen yerden geri adim atilinca tekrardan geri adim kapatilacan kadar acik olur bu haliyle hesaplaniliyor*/
    public boolean lockedCounterOfMovingBackLose = true;
    long counterOfDummyBackMove = 0;
    private  long counterTotalBackStep = 0;
    long totalGameFinishedScore = 0;
    int overLongTotalGameFinishedScore = 0;


    public Score(Game game, Player player) {
        this.game = game;
        this.player = player;
    }

    public void updatePlayedTime() {
        playedTime = player.getTimeKeeper().getTotalPassedTimeDuringPlayingGame();
    }


    public long getTotalGameFinishedScore() {
        return totalGameFinishedScore;
    }

    public void increaseTotalGameFinishedScore() {
        totalGameFinishedScore++;
        if (totalGameFinishedScore == Long.MAX_VALUE) {
            totalGameFinishedScore = 0;
            overLongTotalGameFinishedScore++;
        }
    }

    public int getOverLongTotalGameFinishedScore() {
        return overLongTotalGameFinishedScore;
    }

    public void increaseCounterOfDummyBackMove() {
        counterOfDummyBackMove++;
    }

    public void decreaseCounterOfDummyBackMove() {
        counterOfDummyBackMove--;

    }

    public boolean isLockedCounterOfMovingBackLose() {
        return lockedCounterOfMovingBackLose;
    }

    public void lockCounterOfMovingBackLose() {
        lockedCounterOfMovingBackLose = true;
    }

    public void unlockCounterOfMovingBackLose() {
        lockedCounterOfMovingBackLose = false;
    }

    public long getCounterOfDummyBackMove() {
        return counterOfDummyBackMove;
    }

    public void increaseCounterTotalBackStep() {
        counterTotalBackStep++;
    }

    public void decreaseCounterTotalBackStep() {
        counterTotalBackStep--;
    }

    public long getCounterTotalBackStep() {
        return counterTotalBackStep;
    }
}
