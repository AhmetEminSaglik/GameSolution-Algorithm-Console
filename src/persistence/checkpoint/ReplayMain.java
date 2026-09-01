package persistence.checkpoint;

import persistence.DbConfig;
import persistence.GridPath;

import java.util.List;
import java.util.UUID;

/**
 * Checkpoint replay CLI (Faz B).
 *
 *   java -cp ... persistence.checkpoint.ReplayMain replay <solving_run_id> <from> <to>
 *       -> [from, to] arasindaki cozumleri yeniden uretir ve yazar.
 *
 *   java -cp ... persistence.checkpoint.ReplayMain verify <solving_run_id> <checkpointIndex>
 *       -> <checkpointIndex> checkpoint'inden bir sonrakine oynatir, state'i karsilastirir.
 *          Fark yoksa determinizm dogrulanmis demektir.
 */
public final class ReplayMain {

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
            return;
        }
        DbConfig cfg = DbConfig.load();
        switch (args[0]) {
            case "replay" -> {
                if (args.length != 4) { usage(); return; }
                UUID runId = UUID.fromString(args[1]);
                long from = Long.parseLong(args[2]);
                long to = Long.parseLong(args[3]);
                try (Algo2ReplayEngine engine = new Algo2ReplayEngine(cfg)) {
                    List<GridPath> paths = engine.replay(runId, from, to);
                    System.out.println("Yeniden uretilen cozum sayisi: " + paths.size()
                            + "  (aralik " + from + ".." + to + ")");
                    long idx = from;
                    for (GridPath p : paths) {
                        System.out.println("#" + idx++ + "  start=(" + p.startX() + "," + p.startY()
                                + ")  " + cellsToString(p));
                    }
                }
            }
            case "verify" -> {
                if (args.length != 3) { usage(); return; }
                UUID runId = UUID.fromString(args[1]);
                long cpIndex = Long.parseLong(args[2]);
                try (Algo2ReplayEngine engine = new Algo2ReplayEngine(cfg)) {
                    List<String> diffs = engine.verify(runId, cpIndex);
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
                  ReplayMain replay <solving_run_id> <from> <to>
                  ReplayMain verify <solving_run_id> <checkpointIndex>
                """);
    }
}
