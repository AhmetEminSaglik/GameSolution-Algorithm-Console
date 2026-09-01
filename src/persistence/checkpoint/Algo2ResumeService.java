package persistence.checkpoint;

import game.Game;
import persistence.DbConfig;

import java.util.List;
import java.util.Optional;

/**
 * "Devam et": Main tarafindan kurulmus olan mevcut {@code Game}'i, secilen checkpoint
 * state'ine getirir.
 *
 * Hicbir hata cozucuyu durdurmaz - sorun olursa loglanir ve 0 / bos donulur
 * (Main bunu "bastan basla" olarak yorumlar).
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
     * Verilen {@code solutionIndex} checkpoint'ini {@code game}'e restore eder.
     * @return restore edilen index; {@code 0} = bulunamadi / version uyumsuz / hata.
     */
    public static long restoreFrom(Game game, int algorithmId, long solutionIndex, DbConfig cfg) {
        int row = game.getModel().getRowCount();
        int col = game.getModel().getColCount();
        try (Algo2CheckpointLoader loader = new Algo2CheckpointLoader(cfg)) {
            Optional<Algo2CheckpointRow> found = loader.exact(row, col, algorithmId, solutionIndex);
            if (found.isEmpty()) {
                System.out.println("[checkpoint] #" + solutionIndex + " bulunamadi -> bastan.");
                return 0;
            }
            Algo2CheckpointRow r = found.get();
            if (r.algorithmVersion() != Algo2Snapshot.ALGORITHM_VERSION) {
                System.out.println("[checkpoint] algorithm_version uyusmuyor (" + r.algorithmVersion()
                        + " != " + Algo2Snapshot.ALGORITHM_VERSION + ") -> bastan.");
                return 0;
            }
            Algo2StateRestorer.restore(game, r);
            System.out.println("[checkpoint] devam: #" + r.solutionIndex()
                    + "  (round=" + r.roundCounter() + ", total_solved=" + r.totalSolved() + ")");
            return r.solutionIndex();
        } catch (RuntimeException e) {
            System.err.println("[checkpoint][WARN] devam edilemedi, bastan basliyor: " + e.getMessage());
            return 0;
        }
    }

    /** En son checkpoint'ten devam (--resume argumani icin, sorusuz). */
    public static long resumeLatest(Game game, int algorithmId, DbConfig cfg) {
        List<CheckpointSummary> all;
        try {
            all = list(game.getModel().getRowCount(), game.getModel().getColCount(), algorithmId, cfg);
        } catch (RuntimeException e) {
            System.err.println("[checkpoint][WARN] liste alinamadi, bastan: " + e.getMessage());
            return 0;
        }
        if (all.isEmpty()) {
            System.out.println("[checkpoint] kayit yok -> bastan.");
            return 0;
        }
        return restoreFrom(game, algorithmId, all.get(all.size() - 1).solutionIndex(), cfg);
    }
}
