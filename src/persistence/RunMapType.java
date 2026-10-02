package persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Kosunun hangi baslangic karelerini gezdigi ({@code run_map_type} tablosu,
 * bkz. docker/initdb/11_run_map_type.sql). {@code solver_run} ve
 * {@code solving_checkpoint} satirlari bunun id'sini tasir.
 */
public enum RunMapType {
    /** Tum N² baslangic karesi (simetri yok). */
    ALL(1, "Tum baslangic kareleri gezilir (simetri yok)."),
    /** Sadece simetrinin temel bolgesi (0 <= y <= x <= half); toplam carpanlarla bulunur. */
    UNIQUE(2, "Sadece simetrinin temel bolgesi gezilir (0 <= y <= x <= half); toplam carpanlarla bulunur.");

    /** Bu calistirmada secilen tarama (Main, DB modundan sonra sorar). Varsayilan UNIQUE. */
    private static volatile RunMapType selected = UNIQUE;

    public static RunMapType selected() {
        return selected;
    }

    public static void select(RunMapType type) {
        selected = type;
    }

    /** "1"/"all" → ALL, "2"/"unique" → UNIQUE; bos ya da bilinmeyen → null. */
    public static RunMapType parse(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "1", "all" -> ALL;
            case "2", "unique" -> UNIQUE;
            default -> null;
        };
    }

    private final int id;
    private final String description;

    RunMapType(int id, String description) {
        this.id = id;
        this.description = description;
    }

    public int id() {
        return id;
    }

    /** DB'deki id'yi dondurur; satir yoksa (yeni/eski DB) ekler. */
    public int resolve(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO run_map_type (id, code, description) VALUES (?,?,?) ON CONFLICT (id) DO NOTHING")) {
            ps.setInt(1, id);
            ps.setString(2, name());
            ps.setString(3, description);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM run_map_type WHERE code = ?")) {
            ps.setString(1, name());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("run_map_type'ta " + name() + " yok ve eklenemedi");
                }
                return rs.getInt(1);
            }
        }
    }
}
