package persistence.checkpoint;

import persistence.DbConfig;
import persistence.GridPath;

import java.util.List;

/**
 * Checkpoint replay CLI (Faz B). Anahtar: harita + algoritma (calisma id'si degil).
 *
 *   ReplayMain replay <RxC> <algo> <from> <to>     orn: replay 5x5 2 12001 12400
 *       -> [from, to] arasindaki cozumleri yeniden uretir.
 *
 *   ReplayMain verify <RxC> <algo> <checkpointIndex>   orn: verify 5x5 2 12000
 *       -> checkpoint'ten bir sonrakine oynatir, state'i karsilastirir.
 *          Fark yoksa determinizm dogrulanmis demektir.
 */
public final class ReplayMain {

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
            return;
        }
        DbConfig cfg = DbConfig.load();
        try {
            switch (args[0]) {
                case "replay" -> {
                    if (args.length != 5) { usage(); return; }
                    int[] rc = parseGrid(args[1]);
                    int algo = Integer.parseInt(args[2]);
                    long from = Long.parseLong(args[3]);
                    long to = Long.parseLong(args[4]);
                    try (Algo2ReplayEngine engine = new Algo2ReplayEngine(cfg)) {
                        List<GridPath> paths = engine.replay(rc[0], rc[1], algo, from, to);
                        System.out.println("Yeniden uretilen cozum: " + paths.size()
                                + "  (" + rc[0] + "x" + rc[1] + " algo" + algo + ", aralik " + from + ".." + to + ")");
                        long idx = from;
                        for (GridPath p : paths) {
                            System.out.println("#" + idx++ + "  start=(" + p.startX() + "," + p.startY()
                                    + ")  " + cellsToString(p));
                        }
                    }
                }
                case "verify" -> {
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
                default -> usage();
            }
        } catch (RuntimeException e) {
            System.err.println("HATA: " + e.getMessage());
        }
    }

    private static int[] parseGrid(String s) {
        String[] p = s.toLowerCase().split("x");
        int r = Integer.parseInt(p[0].trim());
        int c = (p.length > 1) ? Integer.parseInt(p[1].trim()) : r;
        return new int[]{r, c};
    }

    private static String cellsToString(GridPath p) {
        StringBuilder sb = new StringBuilder();
        int[][] cells = p.cells();
        int limit = Math.min(cells.length, 12);
        for (int i = 0; i < limit; i++) {
            sb.append('(').append(cells[i][0]).append(',').append(cells[i][1]).append(')');
            if (i < limit - 1) sb.append(' ');
        }
        if (cells.length > limit) sb.append(" ... (").append(cells.length).append(" adim)");
        return sb.toString();
    }

    private static void usage() {
        System.out.println("""
                Kullanim:
                  ReplayMain replay <RxC> <algo> <from> <to>      orn: replay 5x5 2 12001 12400
                  ReplayMain verify <RxC> <algo> <checkpointIndex> orn: verify 5x5 2 12000
                """);
    }
}
