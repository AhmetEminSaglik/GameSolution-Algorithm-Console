package trace;

/**
 * Derleme-zamani sabiti ile "olu kod eleme" (dead-code elimination) yapan
 * hafif iz (trace) yardimcisi.
 *
 * {@link #ENABLED} bir derleme-zamani sabiti (static final + literal) oldugu
 * icin, javac  {@code if (Trace.ENABLED) ...}  bloklarini  ENABLED == false
 * iken BYTECODE'A HIC KOYMAZ (JLS 14.21 - "if (false)" govdesi erisilemez
 * sayilir; Java'da "conditional compilation" bu sekilde yapilir).
 *
 * Yani PROD'da (ENABLED = false) bu satirlarin calisma maliyeti tam sifirdir.
 * "1 trilyon kez if(false)" diye bir sey olmaz -- kod derlenmis halde yoktur.
 *
 * KURAL: cagri yerinde de guard koy:
 *     if (Trace.ENABLED) Trace.log("step " + step + " dir " + dir);
 * Boylece string birlestirme (+ ...) ve metot cagrisinin TAMAMI elenir.
 * Sadece log() icindeki guard'a guvenirsen argumandaki string yine her
 * seferinde uretilir (asil pahali kisim odur, I/O degil).
 *
 * Dogrulama:  javac ... ; javap -c -p <cagri yapan sinif>
 * -> ENABLED=false iken blok bytecode'da gorunmez.
 *
 * PROD (rekor / hiz denemesi) : ENABLED = false
 * TEST (hata ayiklama)        : ENABLED = true  + yeniden derle (tum src)
 */
public final class Trace {

    /** PROD: false. TEST: true. Degistirince TUM kaynagi yeniden derle. */
    public static final boolean ENABLED = false;

    private Trace() {
    }

    public static void log(String message) {
        if (ENABLED) {
            System.out.println("[TRACE] " + message);
        }
    }

    public static void log(String tag, Object value) {
        if (ENABLED) {
            System.out.println("[TRACE][" + tag + "] " + value);
        }
    }
}
