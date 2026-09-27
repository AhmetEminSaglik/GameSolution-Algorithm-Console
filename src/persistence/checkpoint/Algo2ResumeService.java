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

    /** Tum checkpoint surumleri + bu harita/algoritma icin her surumdeki satir sayisi. */
    public static List<CheckpointVersionInfo> versions(int rowSize, int colSize, int algorithmId, DbConfig cfg) {
        try (Algo2CheckpointLoader loader = new Algo2CheckpointLoader(cfg, Algo2CheckpointConfig.load().checkpointVersion())) {
            return loader.versions(rowSize, colSize, algorithmId);
        }
    }

    /** Bu harita + algoritma + checkpoint surumu icin tum checkpoint ozetleri (solution_index artan). */
    public static List<CheckpointSummary> list(int rowSize, int colSize, int algorithmId, int checkpointVersion, DbConfig cfg) {
        try (Algo2CheckpointLoader loader = new Algo2CheckpointLoader(cfg, checkpointVersion)) {
            return loader.listSummaries(rowSize, colSize, algorithmId);
        }
    }

    /**
     * {@code solutionIndex} checkpoint'ini {@code game}'e uygular (tahta + visitedDirections
     * + RoadMemory + sayaclar). Ardindan PlayGame.resumeFrom({@code solutionIndex}) ile
     * oynatilir.
     *
     * @return true = restore edildi; false = bulunamadi / hata (loglandi).
     */
    public static boolean restoreInto(Game game, int algorithmId, int checkpointVersion, long solutionIndex, DbConfig cfg) {
        int row = game.getModel().getRowCount();
        int col = game.getModel().getColCount();
        try (Algo2CheckpointLoader loader = new Algo2CheckpointLoader(cfg, checkpointVersion)) {
            Optional<Algo2CheckpointRow> found = loader.exact(row, col, algorithmId, solutionIndex);
            if (found.isEmpty()) {
                System.out.println("[checkpoint] #" + solutionIndex + " bulunamadi.");
                return false;
            }
            Algo2CheckpointRow r = found.get();
            Algo2StateRestorer.restore(game, r);
            restoreSquareCounter(game, loader, algorithmId, r);
            return true;
        } catch (RuntimeException e) {
            System.err.println("[checkpoint][WARN] restore basarisiz: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kare sayaci (FileTotalScoreCount'a yazilan "[x][y] = N") restore'da sifirdan
     * baslamasin: bu karede daha once bulunan cozumler = solution_index - (onceki
     * karelerin son solution_index'i). solution_index kosular arasi kumulatif oldugu
     * icin bu, kac kez durup devam edilmis olursa olsun dogru sonucu verir.
     *
     * Onceki karenin son cozumu, writer'in kare degisiminde yazdigi sinir satirindan
     * gelir (bkz. Algo2CheckpointWriter.maybeRecord). Bu sinir satiri olmayan eski
     * kosularda bulunan deger en fazla interval kadar geride olabilir - uyari basilir.
     */
    private static void restoreSquareCounter(Game game, Algo2CheckpointLoader loader, int algorithmId, Algo2CheckpointRow r) {
        int startCell = r.path()[0] & 0xFF;
        long before = loader.lastIndexBeforeStartCell(r.rowSize(), r.colSize(), algorithmId, r.solutionIndex(), startCell);
        long squareSolved = r.solutionIndex() - before;
        game.getPlayer().setSquareTotalSolvedValue(squareSolved);
        int x = startCell / r.colSize();
        int y = startCell % r.colSize();
        System.out.println("[checkpoint] kare [" + x + "][" + y + "] sayaci " + squareSolved
                + " ile devam ediyor (onceki kareler toplami=" + before + ")");
        if (before > 0 && before % r.intervalSize() == 0) {
            System.out.println("[checkpoint][WARN] onceki karenin sinir satiri interval'e denk geliyor - "
                    + "eski kosuysa kare sayaci en fazla " + r.intervalSize() + " eksik olabilir.");
        }
    }
}
