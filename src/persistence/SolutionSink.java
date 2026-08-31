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

    @Override
    void close();

    record RunInfo(int rowCount, int colCount, String algorithm) {
    }

    record FoundSolution(long solutionIndex, GridPath path) {
    }

    record RunResult(long totalSolved, long roundCounter, long totalBackSteps, long dummyBackSteps) {
    }
}
