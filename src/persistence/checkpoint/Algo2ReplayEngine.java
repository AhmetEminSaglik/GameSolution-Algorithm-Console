package persistence.checkpoint;

import game.Game;
import game.gamerepo.BuildGame;
import game.gamerepo.player.Player;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.memory.RoadMemory;
import game.gamerepo.player.robot.solution.BaseSolution;
import game.gamerepo.player.robot.solution.second.SecondSolution_CalculateForwardAvailableWays;
import game.location.DirectionLocation;
import game.move.Move;
import game.play.input.robot.RobotInput;
import persistence.DbConfig;
import persistence.GridPath;

import java.util.ArrayList;
import java.util.List;

/**
 * Faz B: bir harita + Algoritma 2 icin kaydedilmis {@code solving_checkpoint}'lerden
 * cozumleri YENIDEN URETIR.
 *
 * {@code <= from} olan son checkpoint yuklenir, cozucu o state'ten deterministik ileri
 * oynatilir; {@code [from, to]} araligindaki cozumler {@link GridPath} olarak toplanir.
 *
 * SADECE Algoritma 2. {@code algorithm_version} eslesmezse reddeder.
 */
public final class Algo2ReplayEngine implements AutoCloseable {

    private final Algo2CheckpointLoader loader;

    public Algo2ReplayEngine(DbConfig cfg) {
        this.loader = new Algo2CheckpointLoader(cfg);
    }

    // ================= replay =================

    public List<GridPath> replay(int row, int col, int algo, long fromIndex, long toIndex) {
        if (fromIndex < 1 || toIndex < fromIndex) {
            throw new IllegalArgumentException("gecersiz aralik: " + fromIndex + ".." + toIndex);
        }
        Algo2CheckpointRow start = loader.latestAtOrBefore(row, col, algo, fromIndex - 1)
                .or(() -> loader.latestAtOrBefore(row, col, algo, fromIndex))
                .orElseThrow(() -> new IllegalStateException(
                        row + "x" + col + " algo" + algo + " icin <= " + fromIndex + " checkpoint yok"));
        checkAlgorithm(start);

        Replay r = start(start);
        List<GridPath> out = new ArrayList<>();
        while (r.currentIndex < toIndex && !r.isGameOver()) {
            boolean solved = r.stepOnce();
            if (solved && r.currentIndex >= fromIndex && r.currentIndex <= toIndex) {
                out.add(r.extractPath());
            }
        }
        return out;
    }

    // ================= verify =================

    /**
     * {@code checkpointIndex} checkpoint'inden bir SONRAKI checkpoint'e oynatir ve varilan
     * state'i o satirla karsilastirir. Bos liste = tam eslesme (determinizm dogrulandi).
     */
    public List<String> verify(int row, int col, int algo, long checkpointIndex) {
        Algo2CheckpointRow start = loader.exact(row, col, algo, checkpointIndex)
                .orElseThrow(() -> new IllegalStateException(
                        row + "x" + col + " algo" + algo + " icin #" + checkpointIndex + " checkpoint yok"));
        Algo2CheckpointRow expected = loader.firstAfter(row, col, algo, checkpointIndex)
                .orElseThrow(() -> new IllegalStateException(
                        "#" + checkpointIndex + " sonrasi checkpoint yok (dogrulanacak hedef yok)"));
        checkAlgorithm(start);

        Replay r = start(start);
        while (r.currentIndex < expected.solutionIndex() && !r.isGameOver()) {
            r.stepOnce();
        }

        List<String> diffs = new ArrayList<>();
        if (r.currentIndex != expected.solutionIndex()) {
            diffs.add("solution_index: replay=" + r.currentIndex + " beklenen=" + expected.solutionIndex()
                    + " (oyun " + (r.isGameOver() ? "bitti" : "devam ediyor") + ")");
            return diffs;
        }
        Player p = r.game.getPlayer();
        RoadMemory road = ((Robot) p).getRobotMemory().getRoadMemory();
        cmp(diffs, "step", p.getStep(), expected.step());
        cmp(diffs, "round_counter", r.game.getRoundCounter(), expected.roundCounter());
        cmp(diffs, "round_counter_overlong", r.game.getOverLongRoundCounter(), expected.roundCounterOverlong());
        cmp(diffs, "total_solved", p.getScore().getTotalGameFinishedScore(), expected.totalSolved());
        cmp(diffs, "total_solved_overlong", p.getScore().getOverLongTotalGameFinishedScore(), expected.totalSolvedOverlong());
        cmp(diffs, "total_back_steps", p.getScore().getCounterTotalBackStep(), expected.totalBackSteps());
        cmp(diffs, "dummy_back_steps", p.getScore().getCounterOfDummyBackMove(), expected.dummyBackSteps());
        cmp(diffs, "locked_back_lose", p.getScore().isLockedCounterOfMovingBackLose(), expected.lockedBackLose());
        cmp(diffs, "square_total_solved", p.getSquareTotalSolvedValue(), expected.squareTotalSolved());
        cmp(diffs, "exit_situation", road.getExitSituation().getSituation(), expected.exitSituation());

        Algo2Snapshot live = Algo2Snapshot.capture(r.game);
        if (!java.util.Arrays.equals(live.path(), expected.path())) {
            diffs.add("path baytlari farkli");
        }
        if (!java.util.Arrays.equals(live.visitedDirs(), expected.visitedDirs())) {
            diffs.add("visited_dirs baytlari farkli");
        }
        if (!java.util.Arrays.equals(live.oneWayList(), expected.oneWayList())) {
            diffs.add("one_way_list baytlari farkli");
        }
        return diffs;
    }

    // ================= ic =================

    private void checkAlgorithm(Algo2CheckpointRow row) {
        if (row.algorithmId() != 2) {
            throw new IllegalStateException("replay yalniz Algoritma 2 icin; algorithm_id=" + row.algorithmId());
        }
        if (row.algorithmVersion() != Algo2Snapshot.ALGORITHM_VERSION) {
            throw new IllegalStateException("checkpoint algorithm_version=" + row.algorithmVersion()
                    + " ama kod " + Algo2Snapshot.ALGORITHM_VERSION + " - eski checkpoint, replay guvenli degil");
        }
    }

    private Replay start(Algo2CheckpointRow row) {
        Game game = Algo2GameBuilder.buildAndRestore(row);
        return new Replay(game, row.solutionIndex());
    }

    private static void cmp(List<String> diffs, String field, long actual, long expected) {
        if (actual != expected) {
            diffs.add(field + ": replay=" + actual + " beklenen=" + expected);
        }
    }

    private static void cmp(List<String> diffs, String field, boolean actual, boolean expected) {
        if (actual != expected) {
            diffs.add(field + ": replay=" + actual + " beklenen=" + expected);
        }
    }

    @Override
    public void close() {
        loader.close();
    }

    /** Tek bir replay oturumu: PlayGame'in cozum dongusunun cekirdegi. */
    static final class Replay {
        final Game game;
        final int totalSquareCount;
        final int lastLocationId;
        long currentIndex;

        Replay(Game game, long startIndex) {
            this.game = game;
            this.totalSquareCount = game.getModel().getTotalSquareCount();
            this.lastLocationId = game.getPlayer().getCompass().getLastLocation();
            this.currentIndex = startIndex;
        }

        boolean isGameOver() {
            return game.getPlayer().getGameRule().isGameOver(game);
        }

        boolean stepOnce() {
            Player player = game.getPlayer();
            game.increaseRoundCounter();
            int choose = player.getInput(game);
            Move move = (choose == lastLocationId)
                    ? player.getPlayerMove().getMoveBack()
                    : player.getPlayerMove().getMoveForward();
            move.move(new DirectionLocation().getLocationValueAccordingToEnteredValue(game, choose));

            if (player.getStep() == totalSquareCount) {
                player.getScore().increaseTotalGameFinishedScore();
                player.increaseSquareTotalSolvedValue();
                currentIndex++;
                return true;
            }
            return false;
        }

        GridPath extractPath() {
            int rows = game.getModel().getRowCount();
            int cols = game.getModel().getColCount();
            int[][] board = game.getModel().getGameSquares();
            int[][] cells = new int[rows * cols][2];
            for (int x = 0; x < rows; x++) {
                for (int y = 0; y < cols; y++) {
                    int step = board[x][y];
                    if (step >= 1 && step <= cells.length) {
                        cells[step - 1][0] = x;
                        cells[step - 1][1] = y;
                    }
                }
            }
            return new GridPath(rows, cols, cells[0][0], cells[0][1], cells);
        }
    }
}
