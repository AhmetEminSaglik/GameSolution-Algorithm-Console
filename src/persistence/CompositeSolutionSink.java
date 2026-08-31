package persistence;

import java.util.List;

/** Birden fazla sink'e ayni olaylari iletir ("both" modu: flat + trie). */
public final class CompositeSolutionSink implements SolutionSink {

    private final List<SolutionSink> sinks;

    public CompositeSolutionSink(List<SolutionSink> sinks) {
        this.sinks = List.copyOf(sinks);
    }

    @Override
    public boolean isEnabled() {
        return sinks.stream().anyMatch(SolutionSink::isEnabled);
    }

    @Override
    public void beginRun(RunInfo info) {
        sinks.forEach(s -> s.beginRun(info));
    }

    @Override
    public void accept(FoundSolution solution) {
        sinks.forEach(s -> s.accept(solution));
    }

    @Override
    public void endRun(RunResult result) {
        sinks.forEach(s -> s.endRun(result));
    }

    @Override
    public void onRoot(int x, int y) {
        sinks.forEach(s -> s.onRoot(x, y));
    }

    @Override
    public void onForward(int step, int x, int y, int move) {
        sinks.forEach(s -> s.onForward(step, x, y, move));
    }

    @Override
    public void onBackward(int steps) {
        sinks.forEach(s -> s.onBackward(steps));
    }

    @Override
    public void close() {
        RuntimeException first = null;
        for (SolutionSink s : sinks) {
            try {
                s.close();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
