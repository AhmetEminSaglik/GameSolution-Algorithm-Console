package persistence.checkpoint;

/**
 * checkpoint_version tablosundan bir surum + bu harita/algoritma icin o surumde
 * kac checkpoint satiri oldugu ("checkpoint'ten devam" surum secimi icin).
 */
public record CheckpointVersionInfo(
        int id,
        String description,
        long rowCount
) {
}
