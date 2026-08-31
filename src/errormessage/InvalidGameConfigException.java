package errormessage;

/**
 * Oyun kurulum degerleri (ornek: kenar sayisi) gecersiz oldugunda firlatilir.
 * Genel {@code Exception} yerine bu tip kullanilir ki cagiran taraf sadece
 * beklenen hatayi yakalasin, gercek programlama hatalarini (NPE vb.) sessizce
 * yutmasin.
 */
public class InvalidGameConfigException extends Exception {

    public InvalidGameConfigException(String message) {
        super(message);
    }
}
