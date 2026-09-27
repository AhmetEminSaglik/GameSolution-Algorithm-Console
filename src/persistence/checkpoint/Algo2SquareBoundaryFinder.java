package persistence.checkpoint;

import game.Game;
import game.gamerepo.BuildGame;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.solution.second.SecondSolution_CalculateForwardAvailableWays;
import game.play.PlayGame;
import game.play.input.robot.RobotInput;
import persistence.DbConfig;
import print.EasylyReadNumber;
import print.FileWriteProcess;
import print.PrintAble;

import java.util.ArrayList;
import java.util.List;

/**
 * DB'deki checkpoint'lerden her baslangic karesinin TAM cozum sayisini bulur
 * (kare sayaci resume'da sifirlandigi icin FileTotalScoreCount'a guvenilemeyen
 * eski kosular icin).
 *
 * Checkpoint'ler sadece her interval'de bir oldugundan kare gecisi iki checkpoint
 * arasinda kalir. Her blogun SON checkpoint'i restore edilip cozucu oradan
 * oynatilir; baslangic karesi degisen ilk cozum bulununca bir onceki solution_index
 * o karenin son cozumudur. Son karenin sonu = DB'deki en buyuk solution_index
 * (kosu sonunda yazilan son snapshot). Hicbir sey DB'ye/rapor dosyalarina yazilmaz,
 * sadece sonuc tablosu rapor/FileTotalScoreCount/SquareBoundaries-*.txt'ye eklenir.
 *
 * Kullanim: {@code Algo2SquareBoundaryFinder <N> [checkpointVersion]}  (verilmezse 1 = eski kayitlar).
 */
public final class Algo2SquareBoundaryFinder {

    private static final int ALGORITHM_ID = 2;
    private static final long PROGRESS_EVERY = 10_000_000L;

    private Algo2SquareBoundaryFinder() {
    }

    /** Kare degisince firlatilir; {@code lastIndex} onceki karenin son cozumu. */
    private static final class SquareChanged extends RuntimeException {
        final long lastIndex;

        SquareChanged(long lastIndex) {
            super(null, null, false, false);
            this.lastIndex = lastIndex;
        }
    }

    private static final PrintAble NO_FILE = new PrintAble() {
        @Override public void write(String text) { }
        @Override public void append(String text) { }
    };

    public static void main(String[] args) {
        int n = Integer.parseInt(args[0]);
        int checkpointVersion = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        DbConfig cfg = DbConfig.load();

        List<long[]> blocks;
        try (Algo2CheckpointLoader loader = new Algo2CheckpointLoader(cfg, checkpointVersion)) {
            blocks = loader.startCellBlocks(n, n, ALGORITHM_ID);
        }
        if (blocks.isEmpty()) {
            System.out.println(n + "x" + n + " checkpoint_version=" + checkpointVersion + " icin checkpoint yok.");
            return;
        }
        System.out.println(n + "x" + n + " checkpoint_version=" + checkpointVersion + ": " + blocks.size() + " kare blogu");

        List<long[]> result = new ArrayList<>();   // {cell, first, last}
        long prevLast = 0;
        for (int b = 0; b < blocks.size(); b++) {
            long[] blk = blocks.get(b);
            int cell = (int) blk[0];
            long last;
            if (b == blocks.size() - 1) {
                last = blk[2];
                System.out.println(label(cell, n) + " son kare: son cozum = DB'deki en buyuk solution_index " + last);
            } else {
                long nextFirst = blocks.get(b + 1)[1];
                System.out.println(label(cell, n) + " son checkpoint " + blk[2] + " -> gecis en gec " + nextFirst
                        + " (en fazla " + (nextFirst - blk[2]) + " cozum oynatilacak)");
                last = replayUntilSquareChanges(cfg, checkpointVersion, n, blk[2], cell);
                System.out.println(label(cell, n) + " son cozum = " + last);
            }
            result.add(new long[]{cell, prevLast + 1, last});
            prevLast = last;
        }

        printAndSave(n, checkpointVersion, result);
    }

    /** {@code fromIndex} checkpoint'inden oynatir; baslangic karesi degisince onceki cozumun index'ini dondurur. */
    private static long replayUntilSquareChanges(DbConfig cfg, int checkpointVersion, int n, long fromIndex, int cell) {
        Game game = newGame(n);
        try (Algo2CheckpointLoader loader = new Algo2CheckpointLoader(cfg, checkpointVersion)) {
            Algo2CheckpointRow row = loader.exact(n, n, ALGORITHM_ID, fromIndex)
                    .orElseThrow(() -> new IllegalStateException("checkpoint yok: " + fromIndex));
            Algo2StateRestorer.restore(game, row);
        }

        CheckpointRecorder detector = new CheckpointRecorder() {
            @Override
            public void maybeRecord(Game g, long solutionIndex) {
                if (startCell(g) != cell) {
                    throw new SquareChanged(solutionIndex - 1);
                }
                if ((solutionIndex - fromIndex) % PROGRESS_EVERY == 0) {
                    System.out.println("   ... " + solutionIndex);
                }
            }

            @Override
            public void close() {
            }
        };

        PlayGame playGame = new PlayGame(game, new persistence.NoOpSolutionSink(), detector);
        playGame.resumeFrom(fromIndex);
        try {
            playGame.playGame();
        } catch (SquareChanged changed) {
            return changed.lastIndex;
        }
        throw new IllegalStateException(label(cell, n) + ": kosu kare degismeden bitti");
    }

    private static int startCell(Game game) {
        int[][] board = game.getModel().getGameSquares();
        int cols = game.getModel().getColCount();
        for (int x = 0; x < board.length; x++) {
            for (int y = 0; y < board[x].length; y++) {
                if (board[x][y] == 1) {
                    return x * cols + y;
                }
            }
        }
        return -1;
    }

    /** Main.runOnce ile ayni kurulum (Robot + Algoritma 2); rapor dosyalarina yazmaz. */
    private static Game newGame(int n) {
        BuildGame build = new BuildGame(n);
        Game game = build.createGame();
        Robot robot = new Robot();
        robot.setGame(game);
        SecondSolution_CalculateForwardAvailableWays solution = new SecondSolution_CalculateForwardAvailableWays(game);
        robot.setSolution(solution);
        robot.setIPlayerInput(new RobotInput(robot.getSolution(), game));
        robot.setPrintableFileTotalScoreCount(NO_FILE);
        robot.setPrintableFileScore(NO_FILE);
        build.createVisitedArea();
        return game;
    }

    /** Kare simetri carpani (bkz. unique-area-calculation.md). */
    private static int multiplier(int x, int y, int n) {
        int half = (n - 1) / 2;
        boolean odd = n % 2 == 1;
        if (odd && x == half && y == half) {
            return 1;
        }
        if (x == y || (odd && x == half)) {
            return 4;
        }
        return 8;
    }

    private static void printAndSave(int n, int checkpointVersion, List<long[]> result) {
        EasylyReadNumber fmt = new EasylyReadNumber();
        StringBuilder sb = new StringBuilder();
        sb.append("==== ").append(n).append('x').append(n).append(" kare sinirlari (checkpoint_version=").append(checkpointVersion)
                .append(", ").append(java.time.LocalDateTime.now().withNano(0)).append(") ====\n");
        long wedge = 0;
        long total = 0;
        for (long[] r : result) {
            int cell = (int) r[0];
            int x = cell / n;
            int y = cell % n;
            long count = r[2] - r[1] + 1;
            int m = multiplier(x, y, n);
            wedge += count;
            total += count * m;
            sb.append(String.format("[%d][%d]  ilk=%-13d son=%-13d  cozum=%-15s x%d = %s%n",
                    x, y, r[1], r[2], fmt.getReadableNumberInStringFormat(count), m,
                    fmt.getReadableNumberInStringFormat(count * m)));
        }
        sb.append("Hesaplanan kareler toplami : ").append(fmt.getReadableNumberInStringFormat(wedge)).append('\n');
        sb.append("Simetriyle TOPLAM          : ").append(fmt.getReadableNumberInStringFormat(total)).append("\n\n");
        System.out.print(sb);
        new FileWriteProcess("FileTotalScoreCount", "SquareBoundaries-" + n + "x" + n).append(sb.toString());
    }

    private static String label(int cell, int n) {
        return "[" + (cell / n) + "][" + (cell % n) + "]";
    }
}
