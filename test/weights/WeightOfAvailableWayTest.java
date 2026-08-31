package weights;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ağırlık dizisi: indeks = o yönden ileride açık kalan kare sayısı,
 * değer = 8 - indeks (açık yol az ise ağırlık yüksek).
 */
class WeightOfAvailableWayTest {

    @Test
    void weightIsEightMinusIndex() {
        double[] w = new WeightOfAvailableWay().getWeightOfDirection();
        assertEquals(9, w.length, "0..8 -> 9 elemanli guvenlik yuvasi dahil");
        assertEquals(8.0, w[0]);
        assertEquals(7.0, w[1]);
        assertEquals(1.0, w[7]);
        assertEquals(0.0, w[8]);
    }
}
