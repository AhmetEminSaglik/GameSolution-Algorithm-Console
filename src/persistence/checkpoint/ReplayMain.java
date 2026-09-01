package persistence.checkpoint;

import persistence.DbConfig;
import persistence.GridPath;

import java.util.List;
import java.util.Scanner;

/**
 * Checkpoint replay CLI (Faz B). Anahtar: harita + algoritma.
 *
 *   ReplayMain replay <RxC> <algo> [from] [to]
 *       from / to verilmezse konsoldan sorar.
 *       to bos ya da 0  → sona kadar (oyun bitene dek).
 *       Cozumler 1'den basladigi icin 0 karisiklik yapmaz.
 *
 *   ReplayMain verify <RxC> <algo> <checkpointIndex>
 *       checkpoint'ten bir sonrakine oynatir, state'i karsilastirir.
 */
public final class ReplayMain {

    public static void main(String[] args) {
        if (args.length < 3) {
            usage();
            return;
        }
        DbConfig cfg = DbConfig.load();
        try {
            switch (args[0]) {
                case "replay" -> runReplay(cfg, args);
                case "verify" -> runVerify(cfg, args);
                default -> usage();
            }
        } catch (RuntimeException e) {
            System.err.println("HATA: " + e.getMessage());
        }
    }

    private static void runReplay(DbConfig cfg, String[] args) {
        int[] rc = parseGrid(args[1]);
        int algo = Integer.parseInt(args[2]);

        long from = (args.length >= 4) ? Long.parseLong(args[3]) : askLong("Baslangic cozum index", 1);
        long to = (args.length >= 5) ? Long.parseLong(args[4])
                : askLong("Bitis cozum index (bos / 0 = sona kadar)", 0);

        try (Algo2ReplayEngine engine = new Algo2ReplayEngine(cfg)) {
            System.out.println("---- " + rc[0] + "x" + rc[1] + " algo" + algo
                    + "  aralik " + from + ".." + (to <= 0 ? "son" : to) + " ----");
            long n = engine.replay(rc[0], rc[1], algo, from, to,
                    (path, idx) -> printSolution(idx, path));
            System.out.println("---- toplam " + n + " cozum ----");
        }
    }

    private static void runVerify(DbConfig cfg, String[] args) {
        if (args.length != 4) { usage(); return; }
        int[] rc = parseGrid(args[1]);
        int algo = Integer.parseInt(args[2]);
        long cpIndex = Long.parseLong(args[3]);
        try (Algo2ReplayEngine engine = new Algo2ReplayEngine(cfg)) {
            List<String> diffs = engine.verify(rc[0], rc[1], algo, cpIndex);
            if (diffs.isEmpty()) {
                System.out.println("OK - #" + cpIndex + " -> sonraki checkpoint TAM ESLESME. Determinizm dogrulandi.");
            } else {
                System.out.println("FARK VAR - #" + cpIndex + ":");
                diffs.forEach(d -> System.out.println("  - " + d));
            }
        }
    }

    // ---- detayli cikti ----

    private static void printSolution(long idx, GridPath p) {
        int[][] cells = p.cells();
        StringBuilder yol = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            yol.append('(').append(cells[i][0]).append(',').append(cells[i][1]).append(')');
            if (i < cells.length - 1) yol.append(' ');
        }
        System.out.println();
        System.out.println("#" + idx + "  start=(" + p.startX() + "," + p.startY()
                + ")  adim=" + p.length());
        System.out.println("  yol: " + yol);
        System.out.println(grid(p));
    }

    /** Adim numaralariyla ASCII grid. */
    private static String grid(GridPath p) {
        int rows = p.rowCount();
        int cols = p.colCount();
        int[][] b = new int[rows][cols];
        int[][] cells = p.cells();
        for (int k = 0; k < cells.length; k++) {
            b[cells[k][0]][cells[k][1]] = k + 1;
        }
        int w = Integer.toString(rows * cols).length();
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < rows; x++) {
            sb.append("  ");
            for (int y = 0; y < cols; y++) {
                sb.append(String.format("%" + w + "d ", b[x][y]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---- yardimci ----

    private static long askLong(String prompt, long emptyDefault) {
        System.out.print(prompt + ": ");
        String in = new Scanner(System.in).nextLine().trim();
        if (in.isEmpty()) return emptyDefault;
        return Long.parseLong(in);
    }

    private static int[] parseGrid(String s) {
        String[] p = s.toLowerCase().split("x");
        int r = Integer.parseInt(p[0].trim());
        int c = (p.length > 1) ? Integer.parseInt(p[1].trim()) : r;
        return new int[]{r, c};
    }

    private static void usage() {
        System.out.println("""
                Kullanim:
                  ReplayMain replay <RxC> <algo> [from] [to]        orn: replay 5x5 2 5000 5000
                     from/to verilmezse sorulur. to bos/0 = sona kadar.
                  ReplayMain verify <RxC> <algo> <checkpointIndex>  orn: verify 5x5 2 1000
                """);
    }
}
