package game.play.report;

import print.EasylyReadNumber;
import print.FileWriteProcess;

/**
 * {@link RunReport}'u formatlayip TEK dosyaya append eder: run-statistic-<map>.txt
 * (orn. run-statistic-5-5.txt). Ayni mapte yapilan her calistirma bu dosyaya birikir.
 */
public class RunReportWriter {

    private final EasylyReadNumber number = new EasylyReadNumber();

    public void append(RunReport report) {
        String fileName = "run-statistic-" + report.map();
        new FileWriteProcess("RunStatistic", fileName).append(format(report));
    }

    private String format(RunReport report) {
        return "Map: " + report.map() + "\n"
                + "Cozum algoritmasi: " + report.algorithm().name() + "\n"
                + "Player: " + report.player().name() + "\n"
                + "Baslangic: " + report.startMode() + "\n"
                + "Db kayit Modu: " + report.dbSaveMode().name() + "\n"
                + "\n"
                + "Total Number Solved: " + number.getReadableNumberInStringFormat(report.totalSolved()) + "\n"
                + "Elapsed time : " + report.elapsedTime() + "\n"
                + "Total Back Step : " + number.getReadableNumberInStringFormat(report.totalBackStep()) + "\n"
                + "Total Step : " + number.getReadableNumberInStringFormat(report.totalStep()) + "\n"
                + "Total Dummy Back Step)  : " + number.getReadableNumberInStringFormat(report.totalDummyBackStep()) + "\n"
                + "\n----------------------------------------\n\n";
    }
}
