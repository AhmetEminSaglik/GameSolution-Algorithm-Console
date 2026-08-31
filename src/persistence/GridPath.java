package persistence;

/**
 * Tek bir cozum yolu: grid boyutu, baslangic hucresi ve adim sirasiyla hucreler.
 * {@code cells[k] = {x, y}} — k = 0 ilk adim (= start), k = rowCount*colCount-1 son adim.
 */
public record GridPath(int rowCount, int colCount, int startX, int startY, int[][] cells) {

    public int length() {
        return cells.length;
    }

    /** Hucre indeksi = x * colCount + y. "Acilis" sutunlari icin kullanilir. */
    public int cellIndexAtStep(int step) {
        return cells[step][0] * colCount + cells[step][1];
    }

    public byte[] encode() {
        return PathCodec.encode(cells);
    }

    public static GridPath decode(byte[] data, int rowCount, int colCount,
                                  int startX, int startY, int pathLength) {
        return new GridPath(rowCount, colCount, startX, startY,
                PathCodec.decode(data, startX, startY, pathLength));
    }
}
