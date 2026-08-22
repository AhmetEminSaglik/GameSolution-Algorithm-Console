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

import java.util.Scanner;


public class Main {

    BaseSolution baseSolution;

    public static void main(String[] args) throws InterruptedException {
        Main main = new Main();
/*
        try {
            openWebpage(new URL("https://www.linkedin.com/in/ahmeteminsaglik"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }*/

//        int squareEdge = 5;
        BuildGame buildGameModel = new BuildGame();

        Game game = buildGameModel.createGame();

        Player player = main.selectPlayer(game);

        buildGameModel.createVisitedArea();

        PlayGame playGame = new PlayGame(game);
        playGame.playGame();
        System.out.println();
        if (main.baseSolution != null)
            System.out.println(main.baseSolution.getClass().getSimpleName());
        System.out.println("Game Dimension : " + game.getModel().getGameSquares().length + "-" + game.getModel().getGameSquares().length);

      /*  BaseSolution baseSolution2 = new SecondSolution_CalculateForwardAvailableWays(game);
        Player robot2 = new Robot(game, baseSolution2);
        buildGameModel.createVisitedArea();

        PlayGame playGame2 = new PlayGame(game);
        playGame2.playGame();


         comparisonOfSolutions = new ComparisonOfSolutions(game);
        comparisonOfSolutions.deleteBothIfTheyAreSame(playGame.comparisonOfSolutions.copyModel, playGame2.comparisonOfSolutions.copyModel);*/
        System.out.println("----------------");


    }

    Player selectPlayer(Game game) {
        System.out.println("Select Player : \nPerson : 1 \n Robot : 2");

        String input = new Scanner(System.in).nextLine();
        if (input.equals("1")) {
            Person person = new Person();
            person.setGame(game);
            person.setIPlayerInput(new PersonInput(game));
            System.out.println("game : " + game.toString());
            return person;
//            return new Person(/*game*/);
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

//            return new Robot(game, baseSolution);

        } else {
            System.out.println("Unknow choice ");
            return selectPlayer(game);
        }

    }

  /*  public static boolean openWebpage(URI uri) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(uri);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean openWebpage(URL url) {
        try {
            return openWebpage(url.toURI());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return false;
    }*/
}

