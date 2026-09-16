package print;

import errormessage.ErrorMessage;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileWriteProcess implements PrintAble, CloseAbleFile {

    /** Butun cikti dosyalari BURAYA toplanir (proje kokune, .idea'nin yanina degil). */
    private static final String REPORT_ROOT = "rapor";

    String fileName;
    BufferedWriter bufferedWriter;

    final boolean WRITE_OVER_FILE = false;
    final boolean APPEND_TO_FILE = true;
    boolean filePrintSituation;

    /** rapor/<name>.txt */
    public FileWriteProcess(String name) {
        this(null, name);
    }

    /** rapor/<name>_<squareLengt>.txt */
    public FileWriteProcess(String name, int squareLengt) {
        this(null, name + "_" + squareLengt);
    }

    /**
     * rapor/<folder>/<name>.txt ({@code folder} null ise rapor/<name>.txt). Cikti turune
     * gore alt klasor secmek icin (orn. "FileScore", "RunStatistic") kullanilir.
     */
    public FileWriteProcess(String folder, String name) {
        Path dir = (folder == null) ? Path.of(REPORT_ROOT) : Path.of(REPORT_ROOT, folder);
        ensureDirectory(dir);
        fileName = dir.resolve(name + ".txt").toString();
    }

    private void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ErrorMessage.appearFatalError(getClass(), e.getMessage());
        }
    }

    void openFile() {
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(fileName, filePrintSituation));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    void appendToFile(String text) {
        updateFileWriteOrAppendCondtion(APPEND_TO_FILE);
        openFile();
        try {
            bufferedWriter.append(text);
        } catch (IOException e) {
            ErrorMessage.appearFatalError(getClass(), e.getMessage());
        } finally {
            closeFile();
        }
    }

    void writeToFile(String text) {
        updateFileWriteOrAppendCondtion(WRITE_OVER_FILE);
        openFile();
        try {
            bufferedWriter.write(text);
        } catch (IOException e) {
            ErrorMessage.appearFatalError(getClass(), e.getMessage());
        } finally {
            closeFile();
        }
    }


    @Override
    public void write(String text) {
        writeToFile(text);
    }

    @Override
    public void append(String text) {
        appendToFile(text);
    }

    @Override
    public void closeFile() {
        try {
            bufferedWriter.close();
        } catch (IOException e) {
            ErrorMessage.appearFatalError(getClass(), e.getMessage());
        }

    }

    void updateFileWriteOrAppendCondtion(boolean newSituation) {
        filePrintSituation = newSituation;
    }

}
