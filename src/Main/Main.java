package Main;

import game.Game;
import game.gamerepo.BuildGame;
import game.gamerepo.player.Player;
import game.gamerepo.player.PlayerType;
import game.gamerepo.player.person.Person;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.gamerepo.player.robot.solution.SolutionAlgorithm;
import game.gamerepo.player.robot.solution.first.FirstSolution_Combination;
import game.gamerepo.player.robot.solution.second.SecondSolution_CalculateForwardAvailableWays;
import game.play.PlayGame;
import game.play.input.person.PersonInput;
import game.play.input.robot.RobotInput;
import game.play.report.RunReport;
import game.play.report.RunReportWriter;
import persistence.CompositeSolutionSink;
import persistence.DbConfig;
import persistence.DbSaveMode;
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
import utility.ConsoleInput;

import java.util.Arrays;
import java.util.List;


public class Main {

    BaseSolution baseSolution;

    /**
     * Her calistirma bitince en bastan (Grid boyutu sorusundan) tekrar baslar; boylece
     * arka arkaya birden fazla test/benchmark yapmak icin projeyi tekrar tekrar
     * calistirmaya (IDE'den yeniden run etmeye) gerek kalmiyor. Her tur icin BuildGame,
     * Game, Player vs. SIFIRDAN olusturuluyor (bkz. runOnce) -> onceki turdan hicbir
     * state kalmaz, calisma aralarinda cakisma olmaz. Girdi biterse (EOF) sessizce cikar.
     */
    public static void main(String[] args) throws InterruptedException {
        while (true) {
            try {
                runOnce(args);
            } catch (java.util.NoSuchElementException eof) {
                System.out.println("Girdi kalmadi, cikiliyor.");
                break;
            }
            System.out.println();
            System.out.println("==================== YENI CALISMA ====================");
        }
    }

    private static void runOnce(String[] args) throws InterruptedException {
        Main main = new Main();

        BuildGame buildGameModel = new BuildGame();

        Game game = buildGameModel.createGame();

        Player player = main.selectPlayer(game);

        buildGameModel.createVisitedArea();

        // Algoritma 2: bastan calistir mi, yoksa checkpoint araligindan devam mi?
        if (wantsCheckpointList(args, main.baseSolution)) {
            runCheckpointRange(game);   // #from checkpoint'ten oynat, #to'da dur
            return;
        }

        DbSaveMode saveMode = readSaveMode(args);
        SolutionSink sink = createSolutionSink(saveMode);
        CheckpointRecorder checkpoint = createCheckpointRecorder(saveMode, args, main.baseSolution, game);
        PlayGame playGame = new PlayGame(game, sink, checkpoint);
        try {
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

        appendRunReport(game, "Bastan Calistir", saveMode, playGame);
    }

    /**
     * Bu calistirmanin ozetini {@link RunReport} DTO'suna doldurup {@link RunReportWriter}
     * ile run-statistic-<map>.txt dosyasina ekler. Hangi mapte, hangi cozum algoritmasi,
     * hangi player, hangi baslangic ve DB kayit modu ile, ne kadar surede kac cozum
     * bulundugunu unutmamak icin.
     */
    private static void appendRunReport(Game game, String startLabel, DbSaveMode saveMode, PlayGame playGame) {
        Player player = game.getPlayer();
        SolutionAlgorithm algorithm = (player instanceof Robot robot)
                ? SolutionAlgorithm.fromOrder(robot.getSolution().getSolutionCreatedOrder())
                : SolutionAlgorithm.NONE;

        RunReport report = new RunReport(
                game.getModel().getRowCount() + "-" + game.getModel().getColCount(),
                algorithm,
                PlayerType.of(player),
                startLabel,
                saveMode,
                player.getScore().getTotalGameFinishedScore(),
                playGame.getElapsedTimeText(),
                player.getScore().getCounterTotalBackStep(),
                game.getRoundCounter(),
                player.getScore().getCounterOfDummyBackMove()
        );
        new RunReportWriter().append(report);
    }

    /**
     * DB kayit modunu {@code mode}'a gore kurar.
     *   flat       : her cozum 1 satir (path_explorer_solution).
     *   trie       : parent-child agac, ortak onek 1 kez (solution_step).
     *   checkpoint : cozum saklanmaz; state snapshot'i {@link #createCheckpointRecorder} ile yazilir.
     *   all        : flat + trie + checkpoint.
     */
    static SolutionSink createSolutionSink(DbSaveMode mode) {
        DbConfig cfg = DbConfig.load();
        switch (mode) {
            case FLAT:
                System.out.println("DB kaydi: FLAT  -> " + cfg.url());
                return new JdbcSolutionSink(cfg);
            case TRIE:
                System.out.println("DB kaydi: TRIE  -> " + cfg.url());
                return new TrieSolutionSink(cfg);
            case BOTH:
                System.out.println("DB kaydi: FLAT + TRIE  -> " + cfg.url());
                return new CompositeSolutionSink(java.util.List.of(
                        new JdbcSolutionSink(cfg), new TrieSolutionSink(cfg)));
            case ALL:
                System.out.println("DB kaydi: FLAT + TRIE + CHECKPOINT  -> " + cfg.url());
                return new CompositeSolutionSink(java.util.List.of(
                        new JdbcSolutionSink(cfg), new TrieSolutionSink(cfg)));
            case CHECKPOINT:
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
    static CheckpointRecorder createCheckpointRecorder(DbSaveMode mode, String[] args, BaseSolution solution, Game game) {
        Algo2CheckpointConfig ccfg = Algo2CheckpointConfig.load();
        boolean on = mode == DbSaveMode.CHECKPOINT || mode == DbSaveMode.ALL
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
        return ConsoleInput.readLine().trim().equals("2");
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
     * Checkpoint'leri listeler, kullanicidan aralik alir; {@code #from} checkpoint'ini
     * game'e restore edip cozucuyu {@link PlayGame} ile oradan oynatir, {@code #to}
     * cozumune gelince durur. Cikti tamamen cozucunun kendi loglarindan +
     * PlayGame'in kosu sonu istatistiginden gelir (bu metod bir sey yazdirmaz).
     *
     * Aralik girdisi (N, M = liste sira numaralari):
     *   "N-M"  → N. checkpoint'in cozum index'inden M. checkpoint'in cozum index'ine.
     *   "N" / "N-"  → N. checkpoint'ten sona kadar.
     *   bos    → bastan sona.
     */
    static void runCheckpointRange(Game game) {
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
        String in = ConsoleInput.readLine().trim();

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

        // #from checkpoint'ini restore et, cozucuyu oradan oynat, #to'da dur.
        // Cikti tamamen cozucunun kendi loglarindan + PlayGame'in alttaki istatistiginden gelir.
        if (!Algo2ResumeService.restoreInto(game, 2, from, DbConfig.load())) {
            return;
        }
        PlayGame playGame = new PlayGame(game, new NoOpSolutionSink(), CheckpointRecorder.NONE);
        playGame.resumeFrom(from);
        if (to > 0) {
            playGame.stopAfter(to);
        }
        playGame.playGame();
        appendRunReport(game, "Checkpoint Araligindan Cozum Goster", DbSaveMode.NONE, playGame);
    }

    private static boolean hasArg(String[] args, String name) {
        for (String a : (args == null ? new String[0] : args)) {
            if (name.equals(a)) {
                return true;
            }
        }
        return false;
    }

    private static DbSaveMode readSaveMode(String[] args) {
        for (String a : (args == null ? new String[0] : args)) {
            if (a.equals("--save-db")) {
                return DbSaveMode.FLAT;
            }
            if (a.startsWith("--save=")) {
                return DbSaveMode.fromArg(a.substring("--save=".length()));
            }
        }
        if (DbConfig.isDbEnabled()) {
            return DbSaveMode.FLAT;
        }
        // argüman yok → sor
        System.out.println("DB kayit modu sec:  0) yok   1) flat   2) trie   3) checkpoint   4) all");
        String in = ConsoleInput.readLine().trim();
        return switch (in) {
            case "1" -> DbSaveMode.FLAT;
            case "2" -> DbSaveMode.TRIE;
            case "3" -> DbSaveMode.CHECKPOINT;
            case "4" -> DbSaveMode.ALL;
            default -> DbSaveMode.NONE;
        };
    }

    Player selectPlayer(Game game) {
        System.out.println("Select Player : \nPerson : 1 \n Robot : 2");

        String input = ConsoleInput.readLine();
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
            input = ConsoleInput.readLine();

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

