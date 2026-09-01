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
import persistence.checkpoint.CheckpointSummary;
import trace.Trace;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;


public class Main {

    BaseSolution baseSolution;

    public static void main(String[] args) throws InterruptedException {
        Main main = new Main();

        BuildGame buildGameModel = new BuildGame();

        Game game = buildGameModel.createGame();

        Player player = main.selectPlayer(game);

        buildGameModel.createVisitedArea();

        // Algoritma 2: bastan calistir mi, yoksa checkpoint araligindan cozum goster mi?
        if (wantsCheckpointList(args, main.baseSolution)) {
            listCheckpointRange(game);   // cozucuyu calistirmaz, sadece araligi yazdirir
            return;
        }

        String saveMode = readSaveMode(args);
        SolutionSink sink = createSolutionSink(saveMode);
        CheckpointRecorder checkpoint = createCheckpointRecorder(saveMode, args, main.baseSolution, game);
        try {
            new PlayGame(game, sink, checkpoint).playGame();
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
     * Algoritma 2 secildikten sonra: bastan calistir mi, yoksa checkpoint araligindan
     * cozum goster mi? Save argumani (sessiz mod) / Algoritma 2 disi → bastan.
     * Yoksa konsoldan sorar.
     */
    static boolean wantsCheckpointList(String[] args, BaseSolution solution) {
        Integer order = (solution == null) ? null : solution.getSolutionCreatedOrder();
        if (order == null || order != 2) {
            return false;
        }
        for (String a : (args == null ? new String[0] : args)) {
            if (a.equals("--save-db") || a.startsWith("--save=")) {
                return false;
            }
        }
        if (DbConfig.isDbEnabled()) {
            return false;
        }
        System.out.println("Baslangic:  1) Bastan calistir   2) Checkpoint araligindan cozum goster");
        return new Scanner(System.in).nextLine().trim().equals("2");
    }

    /** Checkpoint listesini numarali yazdirir. Bos ise false doner. */
    private static boolean printCheckpointList(List<CheckpointSummary> cps, int row, int col) {
        if (cps.isEmpty()) {
            System.out.println("[checkpoint] " + row + "x" + col + " algo2 icin kayit yok.");
            return false;
        }
        System.out.println("Checkpoint'ler (" + row + "x" + col + " algo2):");
        for (int i = 0; i < cps.size(); i++) {
            CheckpointSummary s = cps.get(i);
            System.out.printf("  %2d) #%-8d  round=%-12d  total_solved=%-8d  back=%-10d  dummy=%-9d  %s%n",
                    i + 1, s.solutionIndex(), s.roundCounter(), s.totalSolved(),
                    s.totalBackSteps(), s.dummyBackSteps(), s.createdAt());
        }
        return true;
    }

    private static List<CheckpointSummary> loadCheckpointList(Game game) {
        return Algo2ResumeService.list(game.getModel().getRowCount(),
                game.getModel().getColCount(), 2, DbConfig.load());
    }

    /**
     * Checkpoint'leri gosterir, kullanicidan aralik alir ve o cozumleri
     * {@link persistence.checkpoint.Algo2ReplayEngine} ile yeniden uretip yazdirir.
     * Cozucuyu CALISTIRMAZ.
     *
     * Aralik girdisi (N, M = liste sira numaralari):
     *   "N-M"  → N. checkpoint'in cozum index'inden M. checkpoint'in cozum index'ine.
     *   "N" veya "N-"  → N. checkpoint'ten sona kadar.
     *   bos    → bastan sona.
     */
    static void listCheckpointRange(Game game) {
        int row = game.getModel().getRowCount();
        int col = game.getModel().getColCount();
        List<CheckpointSummary> cps;
        try {
            cps = loadCheckpointList(game);
        } catch (RuntimeException e) {
            System.err.println("[checkpoint][WARN] liste alinamadi: " + e.getMessage());
            return;
        }
        if (!printCheckpointList(cps, row, col)) {
            return;
        }
        System.out.print("Aralik (N-M / N / bos = hepsi): ");
        String in = new Scanner(System.in).nextLine().trim();

        int fromRow = 1;
        Integer toRow = null;   // null = sona kadar
        try {
            if (!in.isEmpty()) {
                int dash = in.indexOf('-');
                if (dash < 0) {
                    fromRow = Integer.parseInt(in.trim());
                } else {
                    fromRow = Integer.parseInt(in.substring(0, dash).trim());
                    String rest = in.substring(dash + 1).trim();
                    if (!rest.isEmpty()) {
                        toRow = Integer.parseInt(rest);
                    }
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("[checkpoint] gecersiz aralik.");
            return;
        }
        if (fromRow < 1 || fromRow > cps.size()
                || (toRow != null && (toRow < fromRow || toRow > cps.size()))) {
            System.out.println("[checkpoint] sira no aralik disi.");
            return;
        }

        long from = cps.get(fromRow - 1).solutionIndex();
        long to = (toRow == null) ? 0 : cps.get(toRow - 1).solutionIndex();  // 0 = sona kadar

        try (persistence.checkpoint.Algo2ReplayEngine engine =
                     new persistence.checkpoint.Algo2ReplayEngine(DbConfig.load())) {
            System.out.println("---- " + row + "x" + col + " algo2  cozum " + from + ".."
                    + (to <= 0 ? "son" : to) + " ----");
            long produced = engine.replay(row, col, 2, from, to,
                    (path, idx) -> printReplaySolution(idx, path));
            System.out.println("---- toplam " + produced + " cozum ----");
        } catch (RuntimeException e) {
            System.err.println("[checkpoint][WARN] replay: " + e.getMessage());
        }
    }

    private static void printReplaySolution(long idx, persistence.GridPath p) {
        int[][] cells = p.cells();
        StringBuilder yol = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            yol.append('(').append(cells[i][0]).append(',').append(cells[i][1]).append(')');
            if (i < cells.length - 1) yol.append(' ');
        }
        int rows = p.rowCount(), cols = p.colCount();
        int[][] b = new int[rows][cols];
        for (int k = 0; k < cells.length; k++) {
            b[cells[k][0]][cells[k][1]] = k + 1;
        }
        int w = Integer.toString(rows * cols).length();
        System.out.println();
        System.out.println("#" + idx + "  start=(" + p.startX() + "," + p.startY() + ")  adim=" + p.length());
        System.out.println("  yol: " + yol);
        for (int x = 0; x < rows; x++) {
            StringBuilder sb = new StringBuilder("  ");
            for (int y = 0; y < cols; y++) {
                sb.append(String.format("%" + w + "d ", b[x][y]));
            }
            System.out.println(sb);
        }
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

