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
import persistence.CompositeSolutionSink;
import persistence.DbConfig;
import persistence.JdbcSolutionSink;
import persistence.NoOpSolutionSink;
import persistence.SolutionSink;
import persistence.TrieSolutionSink;
import persistence.checkpoint.Algo2CheckpointConfig;
import persistence.checkpoint.Algo2CheckpointWriter;
import persistence.checkpoint.Algo2ResumeService;
import persistence.checkpoint.CheckpointRecorder;
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

        // Algoritma 2 icin: bastan basla mi, checkpoint'ten devam mi?
        long resumeIndex = 0;
        boolean resume = wantsResume(args, main.baseSolution);
        String saveMode;
        if (resume) {
            resumeIndex = Algo2ResumeService.resumeInto(game, 2, DbConfig.load()).orElse(0L);
            saveMode = "checkpoint";   // devam edince ilerleme kaydi surer
        } else {
            saveMode = readSaveMode(args);
        }

        SolutionSink sink = createSolutionSink(saveMode);
        CheckpointRecorder checkpoint = createCheckpointRecorder(saveMode, args, main.baseSolution, game);
        try {
            PlayGame playGame = new PlayGame(game, sink, checkpoint);
            if (resumeIndex > 0) {
                playGame.resumeFrom(resumeIndex);
            }
            playGame.playGame();
        } finally {
            checkpoint.close();
            sink.close();
        }
        System.out.println();
        if (main.baseSolution != null)
            System.out.println(main.baseSolution.getClass().getSimpleName());
        System.out.println("Game Dimension : " + game.getModel().getRowCount() + "-" + game.getModel().getColCount());

        System.out.println("----------------");


    }

    /**
     * DB kayit modunu {@code mode}'a gore kurar.
     *   flat       : her cozum 1 satir (path_explorer_solution).
     *   trie       : parent-child agac, ortak onek 1 kez (solution_step).
     *   checkpoint : cozum saklanmaz; state snapshot'i {@link #createCheckpointRecorder} ile yazilir.
     *   all        : flat + trie + checkpoint.
     */
    static SolutionSink createSolutionSink(String mode) {
        DbConfig cfg = DbConfig.load();
        switch (mode) {
            case "flat":
                System.out.println("DB kaydi: FLAT  -> " + cfg.url());
                return new JdbcSolutionSink(cfg);
            case "trie":
                System.out.println("DB kaydi: TRIE  -> " + cfg.url());
                return new TrieSolutionSink(cfg);
            case "both":
                System.out.println("DB kaydi: FLAT + TRIE  -> " + cfg.url());
                return new CompositeSolutionSink(java.util.List.of(
                        new JdbcSolutionSink(cfg), new TrieSolutionSink(cfg)));
            case "all":
                System.out.println("DB kaydi: FLAT + TRIE + CHECKPOINT  -> " + cfg.url());
                return new CompositeSolutionSink(java.util.List.of(
                        new JdbcSolutionSink(cfg), new TrieSolutionSink(cfg)));
            case "checkpoint":
                System.out.println("DB kaydi: CHECKPOINT  -> " + cfg.url());
                return new NoOpSolutionSink();
            default:
                System.out.println("DB kaydi: kapali");
                return new NoOpSolutionSink();
        }
    }

    /**
     * Checkpoint kayit modu (SADECE Algoritma 2). Acilir:
     *   - {@code mode} = {@code checkpoint} veya {@code all}, veya
     *   - {@code --checkpoint} argumani, veya
     *   - {@code checkpoint.enabled=true} (db.properties) / {@code PATHEXPLORER_CHECKPOINT_ENABLED=1}
     * Aralik: {@code checkpoint.interval.<R>x<C>} (orn. 5x5=1000, 6x6=10000), yoksa
     * {@code checkpoint.interval.default}. {@code PATHEXPLORER_CHECKPOINT_INTERVAL} hepsini ezer.
     */
    static CheckpointRecorder createCheckpointRecorder(String mode, String[] args, BaseSolution solution, Game game) {
        Algo2CheckpointConfig ccfg = Algo2CheckpointConfig.load();
        boolean on = mode.equals("checkpoint") || mode.equals("all")
                || hasArg(args, "--checkpoint") || ccfg.isEnabled();
        if (!on) {
            return CheckpointRecorder.NONE;
        }
        Integer order = (solution == null) ? null : solution.getSolutionCreatedOrder();
        if (order == null || order != 2) {
            System.out.println("Checkpoint: yalniz Algoritma 2 icin -> kapali");
            return CheckpointRecorder.NONE;
        }
        DbConfig cfg = DbConfig.load();
        Algo2CheckpointWriter writer = new Algo2CheckpointWriter(
                cfg, ccfg, game.getModel().getRowCount(), game.getModel().getColCount(), order);
        System.out.println("Checkpoint: ACIK  run=" + writer.solvingRunId()
                + "  her " + writer.interval() + " cozumde bir  -> " + cfg.url());
        return writer;
    }

    /**
     * Algoritma 2 secildikten sonra: bastan mi, checkpoint'ten devam mi?
     *   - {@code --resume} argumani → devam.
     *   - Save argumani varsa (sessiz mod) → bastan.
     *   - Yoksa konsoldan sorar.
     * Algoritma 2 disinda her zaman false.
     */
    static boolean wantsResume(String[] args, BaseSolution solution) {
        Integer order = (solution == null) ? null : solution.getSolutionCreatedOrder();
        if (order == null || order != 2) {
            return false;
        }
        if (hasArg(args, "--resume")) {
            return true;
        }
        for (String a : (args == null ? new String[0] : args)) {
            if (a.equals("--save-db") || a.startsWith("--save=")) {
                return false;
            }
        }
        if (DbConfig.isDbEnabled()) {
            return false;
        }
        System.out.println("Baslangic:  1) Bastan basla   2) Checkpoint'ten devam et");
        return new Scanner(System.in).nextLine().trim().equals("2");
    }

    private static boolean hasArg(String[] args, String name) {
        for (String a : (args == null ? new String[0] : args)) {
            if (name.equals(a)) {
                return true;
            }
        }
        return false;
    }

    private static String readSaveMode(String[] args) {
        for (String a : (args == null ? new String[0] : args)) {
            if (a.equals("--save-db")) {
                return "flat";
            }
            if (a.startsWith("--save=")) {
                return a.substring("--save=".length()).toLowerCase();
            }
        }
        if (DbConfig.isDbEnabled()) {
            return "flat";
        }
        // argüman yok → sor
        System.out.println("DB kayit modu sec:  0) yok   1) flat   2) trie   3) checkpoint   4) all");
        String in = new Scanner(System.in).nextLine().trim();
        return switch (in) {
            case "1" -> "flat";
            case "2" -> "trie";
            case "3" -> "checkpoint";
            case "4" -> "all";
            default -> "none";
        };
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

