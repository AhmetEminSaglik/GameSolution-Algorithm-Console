package persistence.checkpoint;

import game.Game;
import persistence.DbConfig;

import java.util.Optional;

/**
 * "Devam et": Main tarafindan kurulmus olan mevcut {@code Game}'i, bu harita +
 * Algoritma 2 icin DB'deki EN SON checkpoint state'ine getirir.
 *
 * Hicbir hata cozucuyu durdurmaz - sorun olursa loglanir ve {@code empty} donulur
 * (Main bunu "bastan basla" olarak yorumlar).
 */
public final class Algo2ResumeService {

    private Algo2ResumeService() {
    }

    /** @return kaldigi solution_index; {@code empty} = checkpoint yok / uyumsuz / hata. */
    public static Optional<Long> resumeInto(Game game, int algorithmId, DbConfig cfg) {
        int row = game.getModel().getRowCount();
        int col = game.getModel().getColCount();
        try (Algo2CheckpointLoader loader = new Algo2CheckpointLoader(cfg)) {
            Optional<Algo2CheckpointRow> latest = loader.latest(row, col, algorithmId);
            if (latest.isEmpty()) {
                System.out.println("[checkpoint] " + row + "x" + col + " algo" + algorithmId
                        + " icin kayit yok -> bastan.");
                return Optional.empty();
            }
            Algo2CheckpointRow r = latest.get();
            if (r.algorithmVersion() != Algo2Snapshot.ALGORITHM_VERSION) {
                System.out.println("[checkpoint] algorithm_version uyusmuyor (" + r.algorithmVersion()
                        + " != " + Algo2Snapshot.ALGORITHM_VERSION + ") -> bastan.");
                return Optional.empty();
            }
            Algo2StateRestorer.restore(game, r);
            System.out.println("[checkpoint] devam: #" + r.solutionIndex()
                    + "  (round=" + r.roundCounter() + ", total_solved=" + r.totalSolved() + ")");
            return Optional.of(r.solutionIndex());
        } catch (RuntimeException e) {
            System.err.println("[checkpoint][WARN] devam edilemedi, bastan basliyor: " + e.getMessage());
            return Optional.empty();
        }
    }
}
