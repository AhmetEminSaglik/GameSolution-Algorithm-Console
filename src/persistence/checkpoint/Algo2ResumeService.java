package persistence.checkpoint;

import persistence.DbConfig;

import java.util.List;

/**
 * Checkpoint listeleme yardimcisi (Algoritma 2). Byte blob cekmeden ozet dondurur.
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
}
