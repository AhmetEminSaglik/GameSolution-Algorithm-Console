package persistence.checkpoint;

import game.Game;
import persistence.DbConfig;

import java.util.List;
import java.util.Optional;

/**
 * Checkpoint listeleme + verilen checkpoint'i mevcut {@code Game}'e restore etme
 * (Algoritma 2). Hicbir hata cozucuyu durdurmaz; sorun olursa loglanip {@code false}
 * / bos donulur.
 */
public final class Algo2ResumeService {

    private Algo2ResumeService() {
    }

    /** Bu harita + algoritma icin tum checkpoint ozetleri (solution_index artan). */
    public static List<CheckpointSummary> list(int rowSize, int colSize, int algorithmId, DbConfig cfg) {
        try (Algo2CheckpointLoader loader = new Algo2CheckpointLoader(cfg)) {
            return loader.listSummaries(rowSize, colSize, algorithmId);
        }
    }

    /**
     * {@code solutionIndex} checkpoint'ini {@code game}'e uygular (tahta + visitedDirections
     * + RoadMemory + sayaclar). Ardindan PlayGame.resumeFrom({@code solutionIndex}) ile
     * oynatilir.
     *
     * @return true = restore edildi; false = bulunamadi / version uyumsuz / hata (loglandi).
     */
    public static boolean restoreInto(Game game, int algorithmId, long solutionIndex, DbConfig cfg) {
        int row = game.getModel().getRowCount();
        int col = game.getModel().getColCount();
        try (Algo2CheckpointLoader loader = new Algo2CheckpointLoader(cfg)) {
            Optional<Algo2CheckpointRow> found = loader.exact(row, col, algorithmId, solutionIndex);
            if (found.isEmpty()) {
                System.out.println("[checkpoint] #" + solutionIndex + " bulunamadi.");
                return false;
            }
            Algo2CheckpointRow r = found.get();
            if (r.algorithmVersion() != Algo2Snapshot.ALGORITHM_VERSION) {
                System.out.println("[checkpoint] algorithm_version uyusmuyor ("
                        + r.algorithmVersion() + " != " + Algo2Snapshot.ALGORITHM_VERSION + ").");
                return false;
            }
            Algo2StateRestorer.restore(game, r);
            return true;
        } catch (RuntimeException e) {
            System.err.println("[checkpoint][WARN] restore basarisiz: " + e.getMessage());
            return false;
        }
    }
}
