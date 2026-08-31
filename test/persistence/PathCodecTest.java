package persistence;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathCodecTest {

    private static final int[][] DIRS = {
            {0, 3}, {2, 2}, {3, 0}, {2, -2}, {0, -3}, {-2, -2}, {-3, 0}, {-2, 2}
    };

    @Test
    void encodeThenDecode_roundTrips_forRandomValidPaths() {
        Random rnd = new Random(42);
        for (int[] size : new int[][]{{5, 5}, {5, 6}, {10, 10}, {12, 9}}) {
            int rows = size[0];
            int cols = size[1];
            for (int trial = 0; trial < 200; trial++) {
                int[][] path = randomWalk(rows, cols, rnd);
                byte[] encoded = PathCodec.encode(path);
                int[][] decoded = PathCodec.decode(encoded, path[0][0], path[0][1], path.length);
                assertArrayEquals(path, decoded, rows + "x" + cols + " round-trip");
            }
        }
    }

    @Test
    void encodedSize_isCeilOfMovesTimesThreeBitsOverEight() {
        // 2 adim = 1 hareket = 3 bit -> 1 byte
        assertEquals(1, PathCodec.encode(new int[][]{{0, 0}, {0, 3}}).length);
        // 3 adim = 2 hareket = 6 bit -> 1 byte
        assertEquals(1, PathCodec.encode(new int[][]{{0, 0}, {0, 3}, {3, 3}}).length);
        // 4 adim = 3 hareket = 9 bit -> 2 byte
        assertEquals(2, PathCodec.encode(new int[][]{{0, 0}, {0, 3}, {3, 3}, {3, 0}}).length);
        // 25 adim = 24 hareket = 72 bit -> 9 byte  (tam 5x5 cozum uzunlugu)
        int[][] full25 = fullWalk(5, 5);
        assertEquals(25, full25.length);
        assertEquals(9, PathCodec.encode(full25).length);
    }

    @Test
    void invalidMove_throws() {
        int[][] bad = {{0, 0}, {1, 1}}; // (1,1) 8 yon vektorunden biri degil
        assertThrows(IllegalArgumentException.class, () -> PathCodec.encode(bad));
    }

    @Test
    void gridPath_encodeDecode_matches() {
        int[][] cells = randomWalk(6, 6, new Random(7));
        GridPath p = new GridPath(6, 6, cells[0][0], cells[0][1], cells);
        GridPath back = GridPath.decode(p.encode(), 6, 6, cells[0][0], cells[0][1], cells.length);
        assertArrayEquals(cells, back.cells());
        assertEquals(cells[1][0] * 6 + cells[1][1], p.cellIndexAtStep(1));
    }

    /** rows x cols grid'i tamamen gezen ilk yolu DFS ile bulur. */
    private static int[][] fullWalk(int rows, int cols) {
        int[][] path = new int[rows * cols][2];
        boolean[][] visited = new boolean[rows][cols];
        visited[0][0] = true;
        path[0] = new int[]{0, 0};
        if (!fill(path, visited, 1, rows, cols)) {
            throw new IllegalStateException("tam yol bulunamadi: " + rows + "x" + cols);
        }
        return path;
    }

    private static boolean fill(int[][] path, boolean[][] visited, int depth, int rows, int cols) {
        if (depth == rows * cols) {
            return true;
        }
        int x = path[depth - 1][0];
        int y = path[depth - 1][1];
        for (int[] d : DIRS) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < rows && ny < cols && !visited[nx][ny]) {
                visited[nx][ny] = true;
                path[depth] = new int[]{nx, ny};
                if (fill(path, visited, depth + 1, rows, cols)) {
                    return true;
                }
                visited[nx][ny] = false;
            }
        }
        return false;
    }

    /** rows x cols grid uzerinde, 8 sicrama vektoruyle rastgele bir basit yol uretir (tam gezmesi sart degil). */
    private static int[][] randomWalk(int rows, int cols, Random rnd) {
        boolean[][] visited = new boolean[rows][cols];
        int x = rnd.nextInt(rows);
        int y = rnd.nextInt(cols);
        visited[x][y] = true;
        java.util.List<int[]> path = new java.util.ArrayList<>();
        path.add(new int[]{x, y});
        for (int step = 0; step < 40; step++) {
            java.util.List<int[]> options = new java.util.ArrayList<>();
            for (int[] d : DIRS) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (nx >= 0 && ny >= 0 && nx < rows && ny < cols && !visited[nx][ny]) {
                    options.add(new int[]{nx, ny});
                }
            }
            if (options.isEmpty()) {
                break;
            }
            int[] next = options.get(rnd.nextInt(options.size()));
            x = next[0];
            y = next[1];
            visited[x][y] = true;
            path.add(new int[]{x, y});
        }
        return path.toArray(new int[0][]);
    }
}
