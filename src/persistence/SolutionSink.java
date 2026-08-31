package persistence;

/**
 * Cozucu (solver) bulunan cozumleri buraya bildirir. Uygulamalar:
 *  - {@link NoOpSolutionSink}  : hicbir sey yapmaz (varsayilan, DB'siz calisma).
 *  - {@code JdbcSolutionSink}  : 1000'lik batch'lerle PostgreSQL'e yazar.
 *
 * Yasam dongusu:  beginRun -> accept* -> endRun -> close
 */
public interface SolutionSink extends AutoCloseable {

    /** false ise cozucu {@link GridPath} cikarma maliyetine bile girmez. */
    default boolean isEnabled() {
        return true;
    }

    void beginRun(RunInfo info);

    void accept(FoundSolution solution);

    void endRun(RunResult result);

    // --- Trie (parent-child agac) modu icin adim-adim olaylar.
    //     Duz sink'ler bunlari umursamaz (default no-op). ---

    /** Yeni baslangic karesi: dongu basinda bir kez + oyun her start karesini degistirdiginde. */
    default void onRoot(int x, int y) {
    }

    /** Ileri adim: adim numarasi {@code step}, varilan kare (x,y), parent'tan gelen yon 0-7. */
    default void onForward(int step, int x, int y, int move) {
    }

    /** Geri adim ({@code steps} kadar; genelde 1). */
    default void onBackward(int steps) {
    }

    @Override
    void close();

    record RunInfo(int rowCount, int colCount, String algorithm) {
    }

    record FoundSolution(long solutionIndex, GridPath path) {
    }

    record RunResult(long totalSolved, long roundCounter, long totalBackSteps, long dummyBackSteps) {
    }
}
