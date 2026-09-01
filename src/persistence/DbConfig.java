package persistence;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * DB baglanti ayarlari. Oncelik: ortam degiskeni > db.properties (classpath/kok) > varsayilan.
 *
 * Ortam degiskenleri:
 *   PATHEXPLORER_DB_URL        (orn. jdbc:postgresql://localhost:5432/pathexplorer?reWriteBatchedInserts=true)
 *   PATHEXPLORER_DB_USER
 *   PATHEXPLORER_DB_PASSWORD
 *   PATHEXPLORER_DB_BATCH_SIZE (varsayilan 1000)
 *   PATHEXPLORER_DB_ENABLED    (1/true ise DB kaydi acik; Main bunu kontrol eder)
 */
public final class DbConfig {

    private static final String DEFAULT_URL =
            "jdbc:postgresql://localhost:5443/pathexplorer?reWriteBatchedInserts=true";
    private static final String DEFAULT_USER = "pathexplorer";
    private static final String DEFAULT_PASSWORD = "pathexplorer";
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final String url;
    private final String user;
    private final String password;
    private final int batchSize;

    private DbConfig(String url, String user, String password, int batchSize) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.batchSize = batchSize;
    }

    public static DbConfig load() {
        Properties p = new Properties();
        // 1) classpath: /db.properties
        try (InputStream in = DbConfig.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
        }
        // 2) calisma dizini: ./db.properties  (classpath'te bulunmadiysa)
        try {
            Path f = Path.of("db.properties");
            if (p.isEmpty() && Files.isReadable(f)) {
                try (InputStream in = Files.newInputStream(f)) {
                    p.load(in);
                }
            }
        } catch (Exception ignored) {
        }
        String url = pick("PATHEXPLORER_DB_URL", p.getProperty("url"), DEFAULT_URL);
        String user = pick("PATHEXPLORER_DB_USER", p.getProperty("user"), DEFAULT_USER);
        String password = pick("PATHEXPLORER_DB_PASSWORD", p.getProperty("password"), DEFAULT_PASSWORD);
        int batch = parseInt(pick("PATHEXPLORER_DB_BATCH_SIZE", p.getProperty("batchSize"), null),
                DEFAULT_BATCH_SIZE);
        return new DbConfig(url, user, password, batch);
    }

    /** Main bu bayragi kontrol eder: DB kaydi acik mi? */
    public static boolean isDbEnabled() {
        String v = System.getenv("PATHEXPLORER_DB_ENABLED");
        return v != null && (v.equals("1") || v.equalsIgnoreCase("true"));
    }

    private static String pick(String envKey, String propValue, String fallback) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env;
        }
        if (propValue != null && !propValue.isBlank()) {
            return propValue;
        }
        return fallback;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return (s == null) ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String url() {
        return url;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public int batchSize() {
        return batchSize;
    }
}
