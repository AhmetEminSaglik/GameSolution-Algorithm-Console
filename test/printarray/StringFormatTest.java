package printarray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Sayıyı "1_234_567" biçimine çeviren saf fonksiyon. Gerçek oracle var. */
class StringFormatTest {

    private final StringFormat stringFormat = new StringFormat();

    @Test
    void groupsDigitsInThreesWithUnderscore() {
        assertEquals("1_234_567", stringFormat.converNumberToReadableNumbers(1_234_567));
        assertEquals("12_400", stringFormat.converNumberToReadableNumbers(12_400));
        assertEquals("999", stringFormat.converNumberToReadableNumbers(999));
        assertEquals("1_000", stringFormat.converNumberToReadableNumbers(1_000));
    }

    @Test
    void handlesZero() {
        assertEquals("0", stringFormat.converNumberToReadableNumbers(0));
    }
}
