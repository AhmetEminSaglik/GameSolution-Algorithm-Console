package Main;

import game.Game;
import game.gamerepo.BuildGame;
import game.gamerepo.player.Player;
import game.gamerepo.player.person.Person;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.gamerepo.player.robot.solution.first.FirstSolution_Combination;
import game.gamerepo.player.robot.solution.second.SecondSolution_CalculateForwardAvailableWays;
import game.play.PlayGame;
import game.play.input.person.PersonInput;
import game.play.input.robot.RobotInput;
import persistence.DbConfig;
import persistence.JdbcSolutionSink;
import persistence.NoOpSolutionSink;
import persistence.SolutionSink;
import trace.Trace;

import java.util.Arrays;
import java.util.Scanner;


public class Main {

    BaseSolution baseSolution;

    public static void main(String[] args) throws InterruptedException {
        Main main = new Main();

        BuildGame buildGameModel = new BuildGame();

        Game game = buildGameModel.createGame();

        Player player = main.selectPlayer(game);

        buildGameModel.createVisitedArea();

        SolutionSink sink = createSolutionSink(args);
        try {
            new PlayGame(game, sink).playGame();
        } finally {
            sink.close();
        }
        System.out.println();
        if (main.baseSolution != null)
            System.out.println(main.baseSolution.getClass().getSimpleName());
        System.out.println("Game Dimension : " + game.getModel().getRowCount() + "-" + game.getModel().getColCount());

        System.out.println("----------------");


    }

    /**
     * DB kaydi acik.sa JdbcSolutionSink, degilse NoOpSolutionSink.
     * Acmak icin:  ortam degiskeni PATHEXPLORER_DB_ENABLED=1  ya da  program argumani --save-db
     * (once "docker compose up -d" ile Postgres ayakta olmali.)
     */
    static SolutionSink createSolutionSink(String[] args) {
        boolean enabled = DbConfig.isDbEnabled()
                || Arrays.asList(args == null ? new String[0] : args).contains("--save-db");
        if (!enabled) {
            System.out.println("DB kaydi: kapali (acmak icin PATHEXPLORER_DB_ENABLED=1 veya --save-db)");
            return new NoOpSolutionSink();
        }
        DbConfig cfg = DbConfig.load();
        System.out.println("DB kaydi: ACIK  -> " + cfg.url());
        return new JdbcSolutionSink(cfg);
    }

    Player selectPlayer(Game game) {
        System.out.println("Select Player : \nPerson : 1 \n Robot : 2");

        String input = new Scanner(System.in).nextLine();
        if (input.equals("1")) {
            Person person = new Person();
            person.setGame(game);
            person.setIPlayerInput(new PersonInput(game));
            if (Trace.ENABLED) Trace.log("game", game);
            return person;

        } else if (input.equals("2")) {
            Robot robot = new Robot();
            robot.setGame(game);
            System.out.println("Please select the solution algorithm : " +
                    "\n1-) First Solution : Combination" +
                    "\n2-) Second Solution : Calculate Forward Ways");
            input = new Scanner(System.in).nextLine();

            if(input.equals("1")){
                baseSolution = new FirstSolution_Combination(game);
            }
            else{
                baseSolution = new SecondSolution_CalculateForwardAvailableWays(game);
            }
            robot.setSolution(baseSolution);
            robot.setIPlayerInput(new RobotInput(robot.getSolution(), game));
            return robot;

        } else {
            System.out.println("Unknown choice");
            return selectPlayer(game);
        }

    }

}

