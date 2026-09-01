package persistence.checkpoint;

import java.sql.Timestamp;

/**
 * "Devam et" listesi icin hafif ozet (byte blob'lar cekilmez).
 */
public record CheckpointSummary(
        long solutionIndex,
        int step,
        long roundCounter,
        long totalSolved,
        long totalBackSteps,
        long dummyBackSteps,
        int squareTotalSolved,
        Timestamp createdAt
) {
}
