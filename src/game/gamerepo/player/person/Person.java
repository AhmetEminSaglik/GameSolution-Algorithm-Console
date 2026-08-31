package game.gamerepo.player.person;

import compass.Compass;
import compass.KeyboardCompass;
import game.Game;
import game.gameover.PersonGameOver;
import game.gamerepo.player.Player;
import game.move.fundamental.MoveBack;
import game.move.fundamental.MoveForward;
import game.play.PlayerMove;
import game.rule.BaseGameRule;

public class Person extends Player {

    public Person() {
        printAbleEveryStep = true;
    }


    @Override
    public Compass getCompass() {
        return new KeyboardCompass();
    }

    @Override
    public String getSolutionName() {
        return getClass().getSimpleName();
    }

    @Override
    public void setGame(Game game) {
        super.setGame(game);
        setPlayerMove();
    }

    @Override
    public void setPlayerMove() {
        playerMove = new PlayerMove(new MoveForward(game), new MoveBack(game));
    }

    @Override
    public BaseGameRule getGameRule() {
        if (gameRule == null) {
            gameRule = new BaseGameRule(new PersonGameOver());
        }
        return gameRule;
    }

    public PlayerMove getPlayerMove() {
        return playerMove;
    }

    @Override
    public boolean isPrintableStepSituation() {
        return printAbleEveryStep;
    }
}
