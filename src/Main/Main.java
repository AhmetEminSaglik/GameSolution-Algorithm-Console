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
import print.EasylyReadNumber;
import trace.Trace;
import utility.ConsoleInput;

import java.util.Arrays;
import java.util.List;


public class Main { // 7x7 eksikler: 5078-7072

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
        // Hangi yol secilirse secilsin, DB kayit modu HER ZAMAN soruluyor (tutarlilik icin).
        boolean checkpointRange = wantsCheckpointList(args, main.baseSolution);
        DbSaveMode saveMode = readSaveMode(args);

        if (checkpointRange) {
            runCheckpointRange(game, saveMode, args, main.baseSolution);   // #from checkpoint'ten oynat, #to'da dur
            return;
        }

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

    /**
     * Checkpoint listesini "checkpoint no" (solution_index / interval) ile yazdirir -
     * liste SIRASI degil, cunku aradaki bir checkpoint eksikse (bkz. gap doldurma)
     * sira numaralari kayar ve N'in anlami degisir. Checkpoint no ise solution_index'e
     * bagli SABIT bir kimlik: eksik olan numara listede gorunmeyerek bosluk gosterir.
     *
     * {@code missingOnly=false}: TUMU tek tek basilir (eskisi gibi).
     * {@code missingOnly=true}: sadece her GERCEK boslugun iki yanindaki (var olan)
     * sinir checkpoint'leri + ilk/son checkpoint basilir; aralarindaki uzun ardisik
     * (boslugu olmayan) bolgeler "N ara checkpoint gizlendi" diye kisaltilir - binlerce
     * satirlik gecmiste TUM gercek bosluklari tek bakista gormek icin.
     *
     * ONEMLI: Algo2CheckpointWriter, oturum kapanirken interval'e denk gelmese bile
     * SON snapshot'i da yazar (bkz. persistLastIfNeeded) - yani solution_index HER
     * ZAMAN interval'in tam kati olmayabilir (orn. 451760). Boyle bir "ara-durak"
     * satiri no=solution_index/interval hesabinda GERCEK bir checkpoint'in numarasiyla
     * CAKISABILIR (451760/100000=4, tipki 400000/100000=4 gibi) - bu da anlamsiz
     * negatif "eksik" sayilarina yol acar. Bu yuzden ara-durak satirlari (solution_index
     * interval'e tam bolunmeyenler) checkpoint NO SIRALAMASINA hic KATILMAZ; ayri, ozel
     * bir etiketle gosterilir.
     *
     * Bos ise false doner.
     */
    private static boolean printCheckpointList(List<CheckpointSummary> cps, int row, int col, int interval, boolean missingOnly) {
        if (cps.isEmpty()) {
            System.out.println("[checkpoint] " + row + "x" + col + " algo2 icin kayit yok.");
            return false;
        }
        System.out.println("Checkpoint'ler (" + row + "x" + col + " algo2, checkpoint no = solution_index/" + interval
                + (missingOnly ? ", SADECE EKSIKLER" : "") + "):");

        List<CheckpointSummary> onGrid = cps.stream()
                .filter(s -> s.solutionIndex() % interval == 0)
                .toList();
        int offGridCount = cps.size() - onGrid.size();

        if (!missingOnly) {
            Long prevNo = null;
            for (CheckpointSummary s : cps) {
                if (s.solutionIndex() % interval != 0) {
                    printAraDurakLine(s);
                    continue;
                }
                long no = s.solutionIndex() / interval;
                if (prevNo != null && no != prevNo + 1) {
                    long missing = no - prevNo - 1;
                    System.out.println("      ... (" + missing + " checkpoint eksik: #" + (prevNo + 1) + "-#" + (no - 1) + ") ...");
                }
                printCheckpointLine(s, interval);
                prevNo = no;
            }
            return true;
        }

        if (offGridCount > 0) {
            System.out.println("      (" + offGridCount + " ara-durak checkpoint bu gorunumde gizli - \"Hepsini goster\" ile gorebilirsin)");
        }
        int n = onGrid.size();
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && (onGrid.get(j + 1).solutionIndex() / interval) == (onGrid.get(j).solutionIndex() / interval) + 1) {
                j++;
            }
            // onGrid[i..j] ardisik (bosluksuz) bir blok - sadece basi + (varsa) sonu basilir.
            printCheckpointLine(onGrid.get(i), interval);
            if (j > i) {
                if (j > i + 1) {
                    System.out.println("      (" + (j - i - 1) + " ara checkpoint gizlendi, hepsi ardisik) ");
                }
                printCheckpointLine(onGrid.get(j), interval);
            }
            if (j + 1 < n) {
                long curNo = onGrid.get(j).solutionIndex() / interval;
                long nextNo = onGrid.get(j + 1).solutionIndex() / interval;
                long missing = nextNo - curNo - 1;
                System.out.println("      ... (" + missing + " checkpoint EKSIK: #" + (curNo + 1) + "-#" + (nextNo - 1) + ") ...");
            }
            i = j + 1;
        }
        return true;
    }

    private static void printCheckpointLine(CheckpointSummary s, int interval) {
        long no = s.solutionIndex() / interval;
        System.out.printf("  %3d) solution_index=%-10d  round=%-12d  total_solved=%-8d  back=%-10d  dummy=%-9d  %s%n",
                no, s.solutionIndex(), s.roundCounter(), s.totalSolved(),
                s.totalBackSteps(), s.dummyBackSteps(), s.createdAt());
    }

    /** Interval'e tam bolunmeyen ("ara-durak"/kapanista yazilmis son snapshot) satir - checkpoint no yok. */
    private static void printAraDurakLine(CheckpointSummary s) {
        System.out.printf("  (ara-durak) solution_index=%-10d  round=%-12d  total_solved=%-8d  back=%-10d  dummy=%-9d  %s%n",
                s.solutionIndex(), s.roundCounter(), s.totalSolved(),
                s.totalBackSteps(), s.dummyBackSteps(), s.createdAt());
    }

    private static List<CheckpointSummary> loadCheckpointList(Game game) {
        return Algo2ResumeService.list(game.getModel().getRowCount(),
                game.getModel().getColCount(), 2, DbConfig.load());
    }

    /**
     * Checkpoint'leri listeler, kullanicidan aralik alir; {@code #from} checkpoint'ini
     * game'e restore edip cozucuyu {@link PlayGame} ile oradan oynatir, {@code #to}
     * cozumune gelince durur (checkpoint yazimi ACIK - bkz. asagi). Cikti tamamen
     * cozucunun kendi loglarindan + PlayGame'in kosu sonu istatistiginden gelir.
     *
     * Aralik girdisi (N, M = CHECKPOINT NO = solution_index/interval, liste sirasi
     * DEGIL - bkz. printCheckpointList):
     *   "N-M"  → N. checkpoint'ten M. checkpoint'in solution_index'ine kadar.
     *            M'nin DB'de ONCEDEN VAR OLMASI GEREKMEZ (henuz kesfedilmemis olabilir
     *            ya da aradaki bir bosluk olabilir - resume calisirken doldurulur).
     *   "N" / "N-"  → N. checkpoint'ten (cozucu dogal olarak bitene kadar) sona kadar.
     *   bos    → listedeki EN KUCUK checkpoint'ten sona kadar.
     * N'in DB'de GERCEKTEN VAR OLMASI SART (restore edilecek state'in kaynagi).
     * Yoksa/gecersizse hata basilip liste TEKRAR gosterilir (tekrar denenebilir).
     */
    static void runCheckpointRange(Game game, DbSaveMode saveMode, String[] args, BaseSolution baseSolution) {
        int row = game.getModel().getRowCount();
        int col = game.getModel().getColCount();
        int interval = Algo2CheckpointConfig.load().intervalFor(row, col);

        while (true) {
            List<CheckpointSummary> cps;
            try {
                cps = loadCheckpointList(game);
            } catch (RuntimeException e) {
                System.err.println("[checkpoint][WARN] liste alinamadi: " + e.getMessage());
                return;
            }
            System.out.print("Liste:  1) Hepsini goster   2) Sadece eksikleri goster: ");
            boolean missingOnly = ConsoleInput.readLine().trim().equals("2");
            if (!printCheckpointList(cps, row, col, interval, missingOnly)) {
                return;
            }
            System.out.print("Aralik (N-M / N / bos = hepsi, N/M = checkpoint no): ");
            String in = ConsoleInput.readLine().trim();

            long fromNo;
            Long toNo = null;   // null = sona kadar
            try {
                if (in.isEmpty()) {
                    fromNo = cps.get(0).solutionIndex() / interval;
                } else {
                    int dash = in.indexOf('-');
                    if (dash < 0) {
                        fromNo = Long.parseLong(in.trim());
                    } else {
                        fromNo = Long.parseLong(in.substring(0, dash).trim());
                        String rest = in.substring(dash + 1).trim();
                        if (!rest.isEmpty()) {
                            toNo = Long.parseLong(rest);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                System.out.println("[checkpoint] gecersiz aralik: '" + in + "'. Tekrar dene.\n");
                continue;
            }

            long from = fromNo * interval;
            boolean fromExists = cps.stream().anyMatch(c -> c.solutionIndex() == from);
            if (!fromExists) {
                System.out.println("[checkpoint] #" + fromNo + " (solution_index=" + from + ") DB'de yok. Tekrar dene.\n");
                continue;
            }
            long to = (toNo == null) ? 0 : toNo * interval;   // 0 = sona kadar; to'nun DB'de olmasi SART DEGIL

            // #from checkpoint'ini restore et, cozucuyu oradan oynat, #to'da dur.
            if (!Algo2ResumeService.restoreInto(game, 2, from, DbConfig.load())) {
                System.out.println("[checkpoint] restore basarisiz. Tekrar dene.\n");
                continue;
            }

            // "Current session" (bu oturumda) delta'lari icin restore hemen sonrasi
            // baslangic degerleri - DB'ye/dosyaya YAZILMAZ, sadece konsola basilir.
            Player player = game.getPlayer();
            long startTotalSolved = player.getScore().getTotalGameFinishedScore();
            long startBackStep = player.getScore().getCounterTotalBackStep();
            long startRoundCounter = game.getRoundCounter();
            long startDummyBackStep = player.getScore().getCounterOfDummyBackMove();

            // Resume sirasinda da YENI checkpoint yazilir (kaldigi yerden "hic durmamis
            // gibi" devam edebilmek icin). Ayni solution_index icin ayni state uretilirse
            // (deterministik algoritma) tam-state UNIQUE constraint bunu sessizce atlar;
            // farkli state uretilirse anomali olarak ayri satir eklenir (bkz.
            // Algo2CheckpointWriter javadoc). Boylece aradaki bosluklar da doldurulur.
            // Artik "bastan calistir" ile AYNI DB kayit modu mekanizmasi: saveMode=yok
            // secilirse resume sirasinda hicbir sey yazilmaz; checkpoint/all secilirse
            // boslukalar doldurulur; flat/trie secilirse bu araliktaki cozumler de
            // ayrica tek tek kaydedilir.
            SolutionSink sink = createSolutionSink(saveMode);
            CheckpointRecorder checkpoint = createCheckpointRecorder(saveMode, args, baseSolution, game);
            PlayGame playGame = new PlayGame(game, sink, checkpoint);
            playGame.resumeFrom(from);
            if (to > 0) {
                playGame.stopAfter(to);
            }
            try {
                playGame.playGame();
            } finally {
                checkpoint.close();
                sink.close();
            }
            long currentTotalSolved = player.getScore().getTotalGameFinishedScore() - startTotalSolved;
            long currentBackStep = player.getScore().getCounterTotalBackStep() - startBackStep;
            long currentStep = game.getRoundCounter() - startRoundCounter;
            long currentDummyBackStep = player.getScore().getCounterOfDummyBackMove() - startDummyBackStep;
            System.out.println();
            System.out.println("---- Bu oturumda (current session, DB'ye kaydedilmez) ----");
            System.out.println("Current Total Solved     : " + new EasylyReadNumber().getReadableNumberInStringFormat(currentTotalSolved));
            System.out.println("Current Total Step       : " + new EasylyReadNumber().getReadableNumberInStringFormat(currentStep));
            System.out.println("Current Total Back Step  : " + new EasylyReadNumber().getReadableNumberInStringFormat(currentBackStep));
            System.out.println("Current Dummy Back Step  : " + new EasylyReadNumber().getReadableNumberInStringFormat(currentDummyBackStep));

            appendRunReport(game, "Checkpoint Araligindan Devam", saveMode, playGame);
            return;
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

