package game.play.report;

import game.gamerepo.player.PlayerType;
import game.gamerepo.player.robot.solution.SolutionAlgorithm;
import persistence.DbSaveMode;

/**
 * Bir PlayGame calismasinin ozeti: hangi mapte, hangi cozum algoritmasi/player,
 * hangi baslangic ve DB kayit modu ile, ne kadar surede kac cozum bulundugu.
 * {@link RunReportWriter} bunu run-statistic-<map>.txt dosyasina append eder.
 */
public record RunReport(
        String map,
        SolutionAlgorithm algorithm,
        PlayerType player,
        String startMode,
        DbSaveMode dbSaveMode,
        long totalSolved,
        String elapsedTime,
        long totalBackStep,
        long totalStep,
        long totalDummyBackStep
) {
}
