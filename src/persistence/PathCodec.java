package persistence;

/**
 * Bir cozum yolunu kompakt {@code byte[]}'a cevirir ve geri okur.
 *
 * <p><b>Yontem: yon-kodlamasi, adim basina 3 bit.</b> Yol = baslangic hucresi +
 * (N-1) sicrama yonu. 8 olasi yon vardir (N/S/E/W = +-3, caprazlar = +-2), her biri
 * 3 bit. N-1 yon bit-packed olarak dizilir -> {@code ceil((N-1)*3 / 8)} byte.
 *
 * <p>Ornek boyutlar (tam cozum, N = rows*cols):
 * <ul>
 *   <li>5x5  (N=25):  24 adim * 3 bit = 72 bit = 9 byte</li>
 *   <li>10x10 (N=100): 99 * 3 = 297 bit = 38 byte</li>
 *   <li>100x100 (N=10000): ~3.7 KB</li>
 * </ul>
 * Portfolyodaki {@code (x<<4)|(y&0x0F)} kodlamasi hucre basina 1 byte ve koordinat
 * basina 4 bit oldugu icin 16x16'da tavan yapar; bu kodlama her grid boyutunda calisir.
 *
 * <p>Decode icin sadece baslangic hucresi ve adim sayisi yeter (yonler deterministik).
 * Basit alternatif (SQL'de sorgulanabilir ama daha buyuk): mutlak hucre indeksi,
 * {@code rows*cols <= 256} ise 1 byte / degilse 2 byte per adim.
 */
public final class PathCodec {

    /**
     * Yon vektorleri (dx, dy). Index = 3-bit kod. Sira compass ile ayni
     * (N=0, NE=1, E=2, SE=3, S=4, SW=5, W=6, NW=7) ama encode/decode tutarli
     * oldugu surece sira onemsiz. Kaynak: src/game/location/direction/*.java
     */
    private static final int[][] DIRS = {
            {0, 3},    // 0 N
            {2, 2},    // 1 NE
            {3, 0},    // 2 E
            {2, -2},   // 3 SE
            {0, -3},   // 4 S
            {-2, -2},  // 5 SW
            {-3, 0},   // 6 W
            {-2, 2},   // 7 NW
    };

    private PathCodec() {
    }

    /**
     * @param cells adim sirasiyla hucreler; {@code cells[k] = {x, y}}, k = 0..N-1.
     * @return bit-packed yon dizisi.
     * @throws IllegalArgumentException ardisik iki hucre arasi hareket gecerli bir yon degilse.
     */
    public static byte[] encode(int[][] cells) {
        if (cells.length < 1) {
            return new byte[0];
        }
        int steps = cells.length - 1;
        byte[] out = new byte[(steps * 3 + 7) / 8];
        int bitPos = 0;
        for (int k = 0; k < steps; k++) {
            int dx = cells[k + 1][0] - cells[k][0];
            int dy = cells[k + 1][1] - cells[k][1];
            int dir = directionIndex(dx, dy);
            for (int b = 2; b >= 0; b--) {
                if (((dir >> b) & 1) != 0) {
                    out[bitPos >> 3] |= (byte) (1 << (7 - (bitPos & 7)));
                }
                bitPos++;
            }
        }
        return out;
    }

    /**
     * @param data      {@link #encode} ciktisi.
     * @param startX     yolun ilk hucresi (adim 1).
     * @param startY     yolun ilk hucresi (adim 1).
     * @param pathLength toplam adim sayisi (tam cozumde rows*cols).
     * @return adim sirasiyla hucreler; {@code result[k] = {x, y}}.
     */
    public static int[][] decode(byte[] data, int startX, int startY, int pathLength) {
        int[][] cells = new int[pathLength][2];
        cells[0][0] = startX;
        cells[0][1] = startY;
        int bitPos = 0;
        for (int k = 1; k < pathLength; k++) {
            int dir = 0;
            for (int b = 0; b < 3; b++) {
                int bit = (data[bitPos >> 3] >> (7 - (bitPos & 7))) & 1;
                dir = (dir << 1) | bit;
                bitPos++;
            }
            cells[k][0] = cells[k - 1][0] + DIRS[dir][0];
            cells[k][1] = cells[k - 1][1] + DIRS[dir][1];
        }
        return cells;
    }

    private static int directionIndex(int dx, int dy) {
        for (int i = 0; i < DIRS.length; i++) {
            if (DIRS[i][0] == dx && DIRS[i][1] == dy) {
                return i;
            }
        }
        throw new IllegalArgumentException("Gecersiz hareket vektoru: (" + dx + ", " + dy + ")");
    }
}
