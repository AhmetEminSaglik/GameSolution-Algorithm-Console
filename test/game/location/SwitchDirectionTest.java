package game.location;

import compass.Compass;
import compass.DirectionCompass;
import compass.KeyboardCompass;
import game.location.direction.East;
import game.location.direction.North;
import game.location.direction.SouthWest;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code choseDirection} bir compass değerini doğru yön nesnesine eşlemeli,
 * geçersiz değerde {@code null} dönmeli. İki farklı compass ile denenerek
 * eşlemenin compass'a göre (sabit index'e değil) yapıldığı doğrulanır.
 */
class SwitchDirectionTest {

    @Test
    void mapsValueToDirection_directionCompass() {
        SwitchDirection sw = new SwitchDirection(new DirectionCompass());
        assertInstanceOf(North.class, sw.choseDirection(0));
        assertInstanceOf(East.class, sw.choseDirection(2));
        assertInstanceOf(SouthWest.class, sw.choseDirection(5));
    }

    @Test
    void mapsValueToDirection_keyboardCompass_usesCompassValuesNotFixedIndex() {
        Compass keyboard = new KeyboardCompass(); // North=8, East=6, SouthWest=1
        SwitchDirection sw = new SwitchDirection(keyboard);
        assertInstanceOf(North.class, sw.choseDirection(8));
        assertInstanceOf(East.class, sw.choseDirection(6));
        assertInstanceOf(SouthWest.class, sw.choseDirection(1));
    }

    @Test
    void invalidValue_returnsNull() {
        PrintStream original = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream())); // hata mesajini yut
        try {
            assertNull(new SwitchDirection(new DirectionCompass()).choseDirection(99));
        } finally {
            System.setOut(original);
        }
    }
}
