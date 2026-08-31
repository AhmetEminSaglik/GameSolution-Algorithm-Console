package persistence;

/** Hicbir sey yapmaz. DB kaydi kapaliyken kullanilir; mevcut davranisi degistirmez. */
public final class NoOpSolutionSink implements SolutionSink {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void beginRun(RunInfo info) {
    }

    @Override
    public void accept(FoundSolution solution) {
    }

    @Override
    public void endRun(RunResult result) {
    }

    @Override
    public void close() {
    }
}
