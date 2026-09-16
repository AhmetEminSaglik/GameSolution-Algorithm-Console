package game.gamerepo.player;

import game.gamerepo.player.robot.Robot;

public enum PlayerType {
    PERSON, ROBOT;

    public static PlayerType of(Player player) {
        return (player instanceof Robot) ? ROBOT : PERSON;
    }
}
