package validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saf mantık birim testi — I/O yok, gerçek "doğru cevap" oracle'ı var.
 * {@code package validation} çünkü {@code calculateValidOrNot} / {@code needToCalculateBySum}
 * paket-private.
 */
class ValidationTest {

    private final Validation validation = new Validation();

    @Test
    void validateSquareNumbers_requiresEdgeStrictlyGreaterThan4() {
        assertFalse(validation.validateSquareNumbers(3));
        assertFalse(validation.validateSquareNumbers(4), "4 gecersiz (kenar > 4 olmali)");
        assertTrue(validation.validateSquareNumbers(5));
        assertTrue(validation.validateSquareNumbers(100));
    }

    @Test
    void needToCalculateBySum_isTrueForNonNegative() {
        assertTrue(validation.needToCalculateBySum(0));
        assertTrue(validation.needToCalculateBySum(3));
        assertFalse(validation.needToCalculateBySum(-1));
    }

    @Test
    void calculateValidOrNot_positiveDelta_staysBelowUpperBound() {
        // maxSquare=5, konum=1, delta=+3 -> 4 < 5  => gecerli
        assertTrue(validation.calculateValidOrNot(5, 1, 3));
        // konum=3, delta=+3 -> 6, degil
        assertFalse(validation.calculateValidOrNot(5, 3, 3));
        // sinir: konum=1, delta=+3 -> 4 (<5 gecerli); konum=2,delta=+3 -> 5 (gecersiz)
        assertTrue(validation.calculateValidOrNot(5, 1, 3));
        assertFalse(validation.calculateValidOrNot(5, 2, 3));
    }

    @Test
    void calculateValidOrNot_negativeDelta_staysAtOrAboveZero() {
        // konum=3, delta=-3 -> 0 >= 0  => gecerli
        assertTrue(validation.calculateValidOrNot(5, 3, -3));
        // konum=2, delta=-3 -> -1  => gecersiz
        assertFalse(validation.calculateValidOrNot(5, 2, -3));
    }
}
