package persistence.checkpoint;

/**
 * {@code solving_checkpoint} tablosundan okunmus ham bir satir. {@link Algo2StateRestorer}
 * bunu bir {@code Game}'e uygular.
 */
public record Algo2CheckpointRow(
        long solutionIndex,
        int rowSize,
        int colSize,
        int algorithmId,
        int algorithmVersion,
        int intervalSize,
        int step,
        int dirCount,
        byte[] path,
        byte[] visitedDirs,
        int exitSituation,
        byte[] oneWayList,
        long roundCounter,
        int roundCounterOverlong,
        long totalSolved,
        int totalSolvedOverlong,
        long totalBackSteps,
        long dummyBackSteps,
        boolean lockedBackLose,
        int squareTotalSolved
) {
}
