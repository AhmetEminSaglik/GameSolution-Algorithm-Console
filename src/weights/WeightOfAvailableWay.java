package weights;


public class WeightOfAvailableWay {

    /** Bir kareden ileri gidilebilecek en fazla yon sayisi (8 yon). */
    private static final int MAX_FORWARD_WAYS = 8;

    /*
     * Indeks = o yonden ileride acik kalan kare sayisi (availableWayNumber).
     * Deger  = MAX_FORWARD_WAYS - indeks  -> acik yol az ise agirlik yuksek
     *          (dar geciti erken tuket, bkz. sozde-kod.md Bolum 2).
     *
     * Pratikte availableWayNumber en fazla 7 olur: incelenen komsu karenin,
     * robotun uzerinde durdugu (ziyaret edilmis) kare her zaman bir komsusudur,
     * yani o yon kapalidir. Indeks 8 bu yuzden asla okunmaz; dizi yine de 0..8
     * boyutunda tutuldu ki beklenmedik bir durumda ArrayIndexOutOfBounds olmasin.
     */
    double weightOfDirection[] = new double[MAX_FORWARD_WAYS + 1];

    public WeightOfAvailableWay() {
        for (int i = 0; i < weightOfDirection.length; i++) {
            weightOfDirection[i] = MAX_FORWARD_WAYS - i;
        }
    }

    public double[] getWeightOfDirection() {
        return weightOfDirection;
    }
}
