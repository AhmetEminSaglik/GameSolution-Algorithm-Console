package persistence.checkpoint;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Checkpoint ayarlari. Oncelik: ortam degiskeni > db.properties > varsayilan.
 *
 * db.properties anahtarlari:
 *   checkpoint.enabled            = true|false
 *   checkpoint.interval.5x5       = 1000
 *   checkpoint.interval.6x6       = 10000
 *   checkpoint.interval.default   = 100000
 *   checkpoint.flushEvery         = 200      (kac checkpoint biriktirince DB'ye yazilsin)
 *
 * Ortam degiskenleri:
 *   PATHEXPLORER_CHECKPOINT_ENABLED   (1/true)
 *   PATHEXPLORER_CHECKPOINT_INTERVAL  (verilirse TUM boyutlar icin bu araligi kullanir)
 */
public final class Algo2CheckpointConfig {

    private static final int DEFAULT_INTERVAL = 100_000;
    private static final int DEFAULT_FLUSH_EVERY = 200;

    private final Properties props;
    private final Integer intervalOverride;
    private final int flushEvery;

    private Algo2CheckpointConfig(Properties props, Integer intervalOverride, int flushEvery) {
        this.props = props;
        this.intervalOverride = intervalOverride;
        this.flushEvery = flushEvery;
    }

    public static Algo2CheckpointConfig load() {
        Properties p = new Properties();
        try (InputStream in = Algo2CheckpointConfig.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
        }
        try {
            Path f = Path.of("db.properties");
            if (p.isEmpty() && Files.isReadable(f)) {
                try (InputStream in = Files.newInputStream(f)) {
                    p.load(in);
                }
            }
        } catch (Exception ignored) {
        }
        Integer override = parseNullableInt(System.getenv("PATHEXPLORER_CHECKPOINT_INTERVAL"));
        int flush = parseInt(p.getProperty("checkpoint.flushEvery"), DEFAULT_FLUSH_EVERY);
        return new Algo2CheckpointConfig(p, override, flush);
    }

    /** Main bu bayragi kontrol eder. Argman ({@code --checkpoint}) da acabilir. */
    public boolean isEnabled() {
        String env = System.getenv("PATHEXPLORER_CHECKPOINT_ENABLED");
        if (env != null && (env.equals("1") || env.equalsIgnoreCase("true"))) {
            return true;
        }
        String v = props.getProperty("checkpoint.enabled");
        return v != null && v.equalsIgnoreCase("true");
    }

    /** Verilen harita icin "kac cozumde bir checkpoint". */
    public int intervalFor(int rowSize, int colSize) {
        if (intervalOverride != null && intervalOverride > 0) {
            return intervalOverride;
        }
        String key = "checkpoint.interval." + rowSize + "x" + colSize;
        Integer specific = parseNullableInt(props.getProperty(key));
        if (specific != null && specific > 0) {
            return specific;
        }
        return parseInt(props.getProperty("checkpoint.interval.default"), DEFAULT_INTERVAL);
    }

    public int flushEvery() {
        return flushEvery;
    }

    private static Integer parseNullableInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String s, int fallback) {
        Integer v = parseNullableInt(s);
        return v == null ? fallback : v;
    }
}
