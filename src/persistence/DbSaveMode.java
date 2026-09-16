package persistence;

/** DB kayit modu: PlayGame'in cozumleri hangi sink(ler)e kaydedecegini belirler. */
public enum DbSaveMode {
    NONE, FLAT, TRIE, CHECKPOINT, BOTH, ALL;

    /** CLI argumanindaki ham metni (flat/trie/checkpoint/both/all) enum'a cevirir. */
    public static DbSaveMode fromArg(String raw) {
        if (raw == null) {
            return NONE;
        }
        return switch (raw.toLowerCase()) {
            case "flat" -> FLAT;
            case "trie" -> TRIE;
            case "checkpoint" -> CHECKPOINT;
            case "both" -> BOTH;
            case "all" -> ALL;
            default -> NONE;
        };
    }
}
