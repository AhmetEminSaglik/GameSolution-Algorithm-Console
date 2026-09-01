package persistence.checkpoint;

import game.Game;

/**
 * PlayGame'in cozum bulununca cagirdigi checkpoint kancasi. Varsayilan {@link #NONE}
 * hicbir sey yapmaz; DB'ye yazan uygulama {@link Algo2CheckpointWriter}.
 *
 * PlayGame yalniz bu arayuze bagli — somut writer'i Main enjekte eder, boylece
 * checkpoint kapaliyken JDBC/Hikari hic yuklenmemis olur.
 */
public interface CheckpointRecorder extends AutoCloseable {

    /**
     * Bir cozum bulunduktan hemen sonra, {@code solutionIndex} artmis haliyle cagrilir.
     * Uygulama kendi araligina gore (orn. her S cozumde bir) state'i saklar.
     */
    void maybeRecord(Game game, long solutionIndex);

    @Override
    void close();

    /** Checkpoint kapali: no-op. */
    CheckpointRecorder NONE = new CheckpointRecorder() {
        @Override public void maybeRecord(Game game, long solutionIndex) { }
        @Override public void close() { }
    };
}
