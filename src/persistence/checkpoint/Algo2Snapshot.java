package persistence.checkpoint;

import game.Game;
import game.gamerepo.player.Player;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.memory.RoadMemory;
import game.gamerepo.player.robot.solution.second.navigation.Navigation;
import game.location.DirectionLocation;
import game.location.LocationsList;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Algoritma 2 cozucusunun bir cozum bulundugu andaki tam deterministik state'i.
 * Sadece getter'lardan okur; oyun nesnelerini DEGISTIRMEZ.
 *
 * Faz A: yalniz yakalama + serilestirme (DB'ye yazma icin). Geri yukleme (replay)
 * Faz B'de eklenecek.
 *
 * Not: {@code path} 1 byte/kare tutar -> hucre indeksi 0..255, yani <= 15x15 harita.
 * Daha buyugu gerekirse short'a cikarilir.
 */
public final class Algo2Snapshot {

    /** Algoritma 2 karar mantiginin surumu. Mantik degisince ARTIR (eski checkpoint'ler gecersiz olur). */
    public static final short ALGO_VERSION = 1;

    private final int rowSize;
    private final int colSize;
    private final int step;
    private final int dirCount;
    private final byte[] path;
    private final byte[] visitedDirs;
    private final int exitSituation;
    private final byte[] oneWayList;

    private final long roundCounter;
    private final int roundCounterOverlong;
    private final long totalSolved;
    private final int totalSolvedOverlong;
    private final long totalBackStep;
    private final long dummyBackMove;
    private final boolean lockedBackLose;
    private final int squareTotalSolved;

    private Algo2Snapshot(Game game) {
        Player player = game.getPlayer();
        Robot robot = (Robot) player;
        int rows = game.getModel().getRowCount();
        int cols = game.getModel().getColCount();
        int n = rows * cols;

        this.rowSize = rows;
        this.colSize = cols;
        this.step = player.getStep();
        this.dirCount = new LocationsList()
                .getListOfLocationsAccordingToPlayerCompass(player.getCompass()).size();

        this.path = packPath(game.getModel().getGameSquares(), rows, cols, n);
        this.visitedDirs = packVisitedDirs(player.getVisitedDirections(), n, dirCount);

        RoadMemory road = robot.getRobotMemory().getRoadMemory();
        this.exitSituation = road.getExitSituation().getSituation();
        this.oneWayList = packOneWayList(road.getOneWayNumbersList());

        this.roundCounter = game.getRoundCounter();
        this.roundCounterOverlong = game.getOverLongRoundCounter();
        this.totalSolved = player.getScore().getTotalGameFinishedScore();
        this.totalSolvedOverlong = player.getScore().getOverLongTotalGameFinishedScore();
        this.totalBackStep = player.getScore().getCounterTotalBackStep();
        this.dummyBackMove = player.getScore().getCounterOfDummyBackMove();
        this.lockedBackLose = player.getScore().isLockedCounterOfMovingBackLose();
        this.squareTotalSolved = player.getSquareTotalSolvedValue();
    }

    /** Cozum bulundugu anda cagir (tahta dolu, step = row*col). */
    public static Algo2Snapshot capture(Game game) {
        return new Algo2Snapshot(game);
    }

    // ---- serilestirme yardimcilari ----

    /** path[k] = (k+1). adimin hucre indeksi (x*cols + y). Bos kalan kareler 0. */
    private static byte[] packPath(int[][] board, int rows, int cols, int n) {
        byte[] out = new byte[n];
        for (int x = 0; x < rows; x++) {
            for (int y = 0; y < cols; y++) {
                int s = board[x][y];
                if (s >= 1 && s <= n) {
                    out[s - 1] = (byte) (x * cols + y);
                }
            }
        }
        return out;
    }

    /** visitedDirections[step][dir] -> bitset, bit index = step * dirCount + dir. */
    private static byte[] packVisitedDirs(boolean[][] vd, int n, int dirCount) {
        int totalBits = n * dirCount;
        byte[] out = new byte[(totalBits + 7) / 8];
        for (int step = 0; step < vd.length && step < n; step++) {
            boolean[] row = vd[step];
            for (int dir = 0; dir < row.length && dir < dirCount; dir++) {
                if (row[dir]) {
                    int bit = step * dirCount + dir;
                    out[bit >>> 3] |= (byte) (1 << (bit & 7));
                }
            }
        }
        return out;
    }

    /** count:int, sonra her Navigation: step:int, oneWayValue:int, compulsoryDirId:int(-1=null), exitLocatedHere:byte. */
    private static byte[] packOneWayList(List<Navigation> list) {
        ByteBuffer buf = ByteBuffer.allocate(4 + list.size() * (4 + 4 + 4 + 1));
        buf.putInt(list.size());
        for (Navigation nav : list) {
            buf.putInt(nav.getStep());
            buf.putInt(nav.getOneWayNumbersValue());
            DirectionLocation comp = nav.getCompulsoryLocation();
            buf.putInt(comp == null ? -1 : comp.getId());
            buf.put((byte) (nav.isExitSituationWasLocatedInThisStep() ? 1 : 0));
        }
        return buf.array();
    }

    // ---- accessor'lar (writer kullanir) ----

    public int rowSize() { return rowSize; }
    public int colSize() { return colSize; }
    public int step() { return step; }
    public int dirCount() { return dirCount; }
    public byte[] path() { return path; }
    public byte[] visitedDirs() { return visitedDirs; }
    public int exitSituation() { return exitSituation; }
    public byte[] oneWayList() { return oneWayList; }
    public long roundCounter() { return roundCounter; }
    public int roundCounterOverlong() { return roundCounterOverlong; }
    public long totalSolved() { return totalSolved; }
    public int totalSolvedOverlong() { return totalSolvedOverlong; }
    public long totalBackStep() { return totalBackStep; }
    public long dummyBackMove() { return dummyBackMove; }
    public boolean lockedBackLose() { return lockedBackLose; }
    public int squareTotalSolved() { return squareTotalSolved; }
}
