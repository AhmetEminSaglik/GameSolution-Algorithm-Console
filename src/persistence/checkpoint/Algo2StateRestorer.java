package persistence.checkpoint;

import compass.Compass;
import game.Game;
import game.gamerepo.player.Player;
import game.gamerepo.player.robot.Robot;
import game.gamerepo.player.robot.memory.RoadMemory;
import game.gamerepo.player.robot.solution.second.navigation.Navigation;
import game.location.DirectionLocation;
import game.location.LocationsList;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Bir {@link Algo2CheckpointRow}'u taze bir {@code Game}/{@code Robot} uzerine uygular:
 * checkpoint alindigi andaki cozucu state'ini AYNEN kurar. Ardindan replay loop
 * (bkz. {@link Algo2ReplayEngine}) deterministik olarak devam eder.
 *
 * {@link Algo2Snapshot} serilestirmesinin tam tersi.
 */
final class Algo2StateRestorer {

    private Algo2StateRestorer() {
    }

    static void restore(Game game, Algo2CheckpointRow row) {
        Player player = game.getPlayer();
        Robot robot = (Robot) player;
        int rows = game.getModel().getRowCount();
        int cols = game.getModel().getColCount();
        int n = rows * cols;

        // 1) tahta + ziyaret edilen alanlar  (path[k] = (k+1). adimin hucre indeksi)
        int[][] board = new int[rows][cols];
        boolean[][] visitedAreas = new boolean[rows][cols];
        int lastCell = -1;
        for (int k = 0; k < row.step() && k < n; k++) {
            int cell = row.path()[k] & 0xFF;
            int x = cell / cols;
            int y = cell % cols;
            board[x][y] = k + 1;
            visitedAreas[x][y] = true;
            lastCell = cell;
        }
        game.getModel().setGameSquares(board);
        game.getModel().setVisitedAreas(visitedAreas);

        // 2) adim + konum
        player.setStep(row.step());
        if (lastCell >= 0) {
            player.getLocation().setX(lastCell / cols);
            player.getLocation().setY(lastCell % cols);
        }

        // 3) visitedDirections[step][dir] bitset
        boolean[][] vd = player.getVisitedDirections();
        for (boolean[] r : vd) {
            java.util.Arrays.fill(r, false);
        }
        int dirCount = row.dirCount();
        int totalBits = n * dirCount;
        byte[] bits = row.visitedDirs();
        for (int bit = 0; bit < totalBits; bit++) {
            if ((bits[bit >>> 3] & (1 << (bit & 7))) != 0) {
                int step = bit / dirCount;
                int dir = bit % dirCount;
                if (step < vd.length && dir < vd[step].length) {
                    vd[step][dir] = true;
                }
            }
        }

        // 4) RoadMemory: exitSituation + oneWayNumbersList
        RoadMemory road = robot.getRobotMemory().getRoadMemory();
        road.updateExistSituation(row.exitSituation());
        List<Navigation> list = road.getOneWayNumbersList();
        list.clear();
        list.addAll(decodeOneWayList(row.oneWayList(), player.getCompass()));

        // 5) sayaclar (metrik surekliligi)
        game.restoreRoundCounter(row.roundCounter(), row.roundCounterOverlong());
        player.getScore().restoreCounters(
                row.totalSolved(), row.totalSolvedOverlong(),
                row.totalBackSteps(), row.dummyBackSteps(), row.lockedBackLose());
        player.restoreSquareTotalSolvedValue(row.squareTotalSolved());
    }

    /** int count, sonra her nav: int step, int oneWayValue, int compulsoryDirId(-1=null), byte exitLocatedHere. */
    private static List<Navigation> decodeOneWayList(byte[] data, Compass compass) {
        List<Navigation> out = new ArrayList<>();
        if (data == null || data.length < 4) {
            return out;
        }
        List<DirectionLocation> dirs = new LocationsList()
                .getListOfLocationsAccordingToPlayerCompass(compass);
        ByteBuffer buf = ByteBuffer.wrap(data);
        int count = buf.getInt();
        for (int i = 0; i < count; i++) {
            Navigation nav = new Navigation();
            nav.setStep(buf.getInt());
            nav.setOneWayNumbersValue(buf.getInt());
            int compId = buf.getInt();
            nav.setExitSituationWasLocatedInThisStep(buf.get() != 0);
            if (compId >= 0) {
                nav.setCompulsoryLocation(directionById(dirs, compId));
            }
            out.add(nav);
        }
        return out;
    }

    private static DirectionLocation directionById(List<DirectionLocation> dirs, int id) {
        for (DirectionLocation d : dirs) {
            if (d.getId() == id) {
                return d;
            }
        }
        return dirs.get(dirs.size() - 1); // LastLocation
    }
}
