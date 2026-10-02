package persistence;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Kosunun yapildigi bilgisayar ({@code machine} tablosu, bkz. docker/initdb/12_ ve 13_).
 * Sadece hizi etkileyen (ya da iki makineyi karsilastirirken gereken) alanlar tutulur:
 * kasa/laptop, islemci, RAM, disk, ekran karti, OS, Java, guc plani. Java'dan
 * okunabilenler her zaman; gerisi Windows'ta PowerShell (CIM) ile okunur, okunamazsa
 * bos kalir. Turbo frekansi okunamiyor - elle doldurulur.
 *
 * Ayni (hostname, cpu_model, ram_gb, os, java) varsa o satir kullanilir ve bos
 * alanlari doldurulur (yeni eklenen kolonlar icin). Process basina bir kez cozulur.
 */
public final class MachineInfo {

    private enum Kind { TEXT, INT, GHZ_FROM_MHZ, MB_FROM_KB }

    private record Col(String column, Kind kind) { }

    /** machine kolonu → detect() anahtari ayni ad (cpu_base_ghz ← cpu_base_mhz, *_mb ← *_kb). */
    private static final List<Col> COLS = List.of(
            new Col("hostname", Kind.TEXT), new Col("chassis_type_id", Kind.INT), new Col("model", Kind.TEXT),
            new Col("os_family_id", Kind.INT), new Col("cpu_vendor_id", Kind.INT), new Col("cpu_arch", Kind.TEXT),
            new Col("cpu_model", Kind.TEXT),
            new Col("cpu_cores", Kind.INT), new Col("cpu_threads", Kind.INT),
            new Col("cpu_base_ghz", Kind.GHZ_FROM_MHZ), new Col("cpu_l2_mb", Kind.MB_FROM_KB),
            new Col("cpu_l3_mb", Kind.MB_FROM_KB),
            new Col("ram_gb", Kind.INT), new Col("ram_type", Kind.TEXT), new Col("ram_rated_mts", Kind.INT),
            new Col("ram_mts", Kind.INT), new Col("ram_modules", Kind.INT),
            new Col("ram_manufacturer", Kind.TEXT), new Col("ram_part", Kind.TEXT),
            new Col("disk", Kind.TEXT), new Col("disk_type", Kind.TEXT), new Col("disk_bus", Kind.TEXT),
            new Col("gpu_model", Kind.TEXT), new Col("gpu_vram_mb", Kind.INT),
            new Col("os", Kind.TEXT), new Col("os_build", Kind.TEXT), new Col("java", Kind.TEXT),
            new Col("jvm_max_heap_mb", Kind.INT), new Col("gc", Kind.TEXT),
            new Col("db_version", Kind.TEXT), new Col("power_plan", Kind.TEXT));

    private static Integer cachedId;

    private MachineInfo() {
    }

    /** machine.id; satir yoksa ekler, varsa bos alanlarini doldurur. Hata olursa null. */
    public static synchronized Integer resolve(Connection c) {
        if (cachedId != null) {
            return cachedId;
        }
        try {
            Map<String, String> m = detect();
            m.put("db_version", dbVersion(c));
            m.put("os_family_id", lookup(c, "os_family", m.remove("os_family_code")));
            m.put("chassis_type_id", lookup(c, "chassis_type", m.remove("chassis_code")));
            m.put("cpu_vendor_id", lookup(c, "cpu_vendor", m.remove("cpu_vendor_code")));

            StringBuilder cols = new StringBuilder();
            StringBuilder marks = new StringBuilder();
            for (Col col : COLS) {
                if (!cols.isEmpty()) {
                    cols.append(", ");
                    marks.append(",");
                }
                cols.append(col.column());
                marks.append("?");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO machine (" + cols + ") VALUES (" + marks
                    + ") ON CONFLICT ON CONSTRAINT machine_identity_key DO NOTHING")) {
                bindAll(ps, m, 1);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT id FROM machine
                     WHERE hostname = ? AND cpu_model IS NOT DISTINCT FROM ? AND ram_gb IS NOT DISTINCT FROM ?
                       AND os = ? AND java = ?
                    """)) {
                ps.setString(1, m.get("hostname"));
                ps.setString(2, m.get("cpu_model"));
                bind(ps, 3, Kind.INT, m.get("ram_gb"));
                ps.setString(4, m.get("os"));
                ps.setString(5, m.get("java"));
                try (ResultSet rs = ps.executeQuery()) {
                    cachedId = rs.next() ? rs.getInt(1) : null;
                }
            }
            if (cachedId != null) {
                fillMissing(c, cachedId, m);
            }
        } catch (SQLException | RuntimeException e) {
            System.out.println("[machine] bilgisayar kaydedilemedi, machine_id bos kalacak: " + e.getMessage());
        }
        return cachedId;
    }

    /** Var olan satirda sadece NULL alanlari doldurur (elle girilen degerler ezilmez). */
    private static void fillMissing(Connection c, int id, Map<String, String> m) throws SQLException {
        StringBuilder set = new StringBuilder();
        for (Col col : COLS) {
            if (!set.isEmpty()) set.append(", ");
            set.append(col.column()).append(" = coalesce(").append(col.column()).append(", ?)");
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE machine SET " + set + " WHERE id = ?")) {
            int next = bindAll(ps, m, 1);
            ps.setInt(next, id);
            ps.executeUpdate();
        }
    }

    private static int bindAll(PreparedStatement ps, Map<String, String> m, int start) throws SQLException {
        int i = start;
        for (Col col : COLS) {
            String key = switch (col.kind()) {
                case GHZ_FROM_MHZ -> col.column().replace("_ghz", "_mhz");
                case MB_FROM_KB -> col.column().replace("_mb", "_kb");
                default -> col.column();
            };
            bind(ps, i++, col.kind(), m.get(key));
        }
        return i;
    }

    private static void bind(PreparedStatement ps, int i, Kind kind, String v) throws SQLException {
        if (kind == Kind.TEXT) {
            if (v == null || v.isBlank()) ps.setNull(i, Types.VARCHAR); else ps.setString(i, v);
            return;
        }
        Long n = parse(v);
        if (n == null) {
            ps.setNull(i, kind == Kind.GHZ_FROM_MHZ ? Types.NUMERIC : Types.INTEGER);
            return;
        }
        switch (kind) {
            case GHZ_FROM_MHZ -> ps.setBigDecimal(i, java.math.BigDecimal.valueOf(n / 1000.0));
            case MB_FROM_KB -> ps.setInt(i, (int) (n / 1024));
            default -> ps.setInt(i, n.intValue());
        }
    }

    static Map<String, String> detect() {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            m.put("hostname", InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            m.put("hostname", System.getenv().getOrDefault("COMPUTERNAME", "bilinmiyor"));
        }
        m.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        m.put("java", System.getProperty("java.vendor") + " " + System.getProperty("java.runtime.version"));
        m.put("cpu_threads", String.valueOf(Runtime.getRuntime().availableProcessors()));
        m.put("jvm_max_heap_mb", String.valueOf(Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            m.put("ram_gb", String.valueOf(Math.round(os.getTotalMemorySize() / (1024.0 * 1024 * 1024))));
        }
        StringBuilder gc = new StringBuilder();
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!gc.isEmpty()) gc.append(", ");
            gc.append(b.getName());
        }
        m.put("gc", gc.toString());
        String arch = System.getProperty("os.arch", "");
        m.put("cpu_arch", switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch;
        });
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("windows")) {
            m.put("os_family_code", "WINDOWS");
            m.putAll(windowsHardware());
        } else if (osName.contains("mac")) {
            m.put("os_family_code", "MACOS");
            m.putAll(macHardware());
        } else if (osName.contains("linux")) {
            m.put("os_family_code", "LINUX");
            m.putAll(linuxHardware());
        }
        return m;
    }

    /** macOS: system_profiler + sysctl. Kasa tipi model adindan (MacBook → Laptop). */
    private static Map<String, String> macHardware() {
        Map<String, String> m = new LinkedHashMap<>();
        Map<String, String> hw = new LinkedHashMap<>();
        for (String line : run(List.of("system_profiler", "SPHardwareDataType"))) {
            int colon = line.indexOf(':');
            if (colon > 0) hw.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        String modelName = hw.get("Model Name");
        m.put("model", modelName == null ? null : "Apple " + modelName);
        String chip = hw.getOrDefault("Chip", hw.get("Processor Name"));
        m.put("cpu_model", chip);
        if (chip != null) {
            m.put("cpu_vendor_code", chip.startsWith("Apple") ? "APPLE" : chip.contains("Intel") ? "INTEL" : null);
        }
        List<String> cores = run(List.of("sysctl", "-n", "hw.physicalcpu"));
        if (!cores.isEmpty()) m.put("cpu_cores", cores.get(0).trim());
        if (modelName != null) {
            m.put("chassis_code", modelName.contains("MacBook") ? "LAPTOP"
                    : modelName.contains("iMac") ? "ALL_IN_ONE"
                    : modelName.contains("mini") ? "MINI_PC" : "DESKTOP");
        }
        List<String> ver = run(List.of("sw_vers", "-productVersion"));
        if (!ver.isEmpty()) m.put("os_build", "macOS " + ver.get(0).trim());
        return m;
    }

    /** Linux: /proc/cpuinfo ve DMI kasa tipi (SMBIOS kodu Windows'takiyle ayni). */
    private static Map<String, String> linuxHardware() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : run(List.of("cat", "/proc/cpuinfo"))) {
            if (line.startsWith("model name") && !m.containsKey("cpu_model")) {
                m.put("cpu_model", line.substring(line.indexOf(':') + 1).trim());
            } else if (line.startsWith("vendor_id") && !m.containsKey("cpu_vendor_code")) {
                String v = line.substring(line.indexOf(':') + 1).trim();
                m.put("cpu_vendor_code", v.equals("GenuineIntel") ? "INTEL" : v.equals("AuthenticAMD") ? "AMD" : null);
            }
        }
        List<String> chassis = run(List.of("cat", "/sys/class/dmi/id/chassis_type"));
        m.put("chassis_code", chassis(chassis.isEmpty() ? null : chassis.get(0).trim(), null));
        return m;
    }

    private static List<String> run(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            List<String> out;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = r.lines().toList();
            }
            p.waitFor(20, TimeUnit.SECONDS);
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** PowerShell/CIM ile key=value satirlari; basarisizsa bos. */
    private static Map<String, String> windowsHardware() {
        String script = String.join("; ",
                "$c=Get-CimInstance Win32_Processor | Select-Object -First 1",
                "'cpu_model=' + $c.Name.Trim()",
                "'cpu_manufacturer_raw=' + $c.Manufacturer",
                "'cpu_cores=' + $c.NumberOfCores",
                "'cpu_base_mhz=' + $c.MaxClockSpeed",
                "'cpu_l2_kb=' + $c.L2CacheSize",
                "'cpu_l3_kb=' + $c.L3CacheSize",
                "$s=Get-CimInstance Win32_ComputerSystem",
                "'model=' + $s.Manufacturer + ' ' + $s.Model",
                "'chassis_smbios=' + ((Get-CimInstance Win32_SystemEnclosure).ChassisTypes | Select-Object -First 1)",
                "'battery=' + @(Get-CimInstance Win32_Battery).Count",
                "$r=@(Get-CimInstance Win32_PhysicalMemory)",
                "'ram_modules=' + $r.Count",
                "'ram_rated_mts=' + $r[0].Speed",
                "'ram_mts=' + $r[0].ConfiguredClockSpeed",
                "'ram_type_code=' + $r[0].SMBIOSMemoryType",
                "'ram_manufacturer_raw=' + $r[0].Manufacturer",
                "'ram_part=' + $r[0].PartNumber.Trim()",
                "$d=Get-PhysicalDisk | Select-Object -First 1",
                "'disk=' + $d.FriendlyName + ' ' + [math]::Round($d.Size/1e9) + ' GB'",
                "'disk_type=' + $d.MediaType",
                "'disk_bus=' + $d.BusType",
                "$g=Get-CimInstance Win32_VideoController | Select-Object -First 1",
                "'gpu_model=' + $g.Name",
                "'gpu_vram_mb=' + [math]::Round($g.AdapterRAM/1MB)",
                "$o=Get-CimInstance Win32_OperatingSystem",
                "'os_build=' + $o.Caption + ' (build ' + $o.BuildNumber + ')'",
                "$p=(powercfg /getactivescheme) -join ' '",
                "if ($p -match '\\((.+)\\)') { 'power_plan=' + $Matches[1] }");
        Map<String, String> m = new LinkedHashMap<>();
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq > 0 && eq < line.length() - 1) {
                        m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
            }
            p.waitFor(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            return Map.of();
        }
        String ramType = m.remove("ram_type_code");
        if (ramType != null) {
            m.put("ram_type", switch (ramType) {
                case "34" -> "DDR5";
                case "26" -> "DDR4";
                case "24" -> "DDR3";
                default -> "SMBIOS " + ramType;
            });
        }
        String cpuMaker = m.remove("cpu_manufacturer_raw");
        if (cpuMaker != null) {
            m.put("cpu_vendor_code", switch (cpuMaker) {
                case "GenuineIntel" -> "INTEL";
                case "AuthenticAMD" -> "AMD";
                default -> cpuMaker.toUpperCase();
            });
        }
        String ramMaker = m.remove("ram_manufacturer_raw");
        if (ramMaker != null) {
            m.put("ram_manufacturer", ramVendor(ramMaker));
        }
        m.put("chassis_code", chassis(m.remove("chassis_smbios"), m.remove("battery")));
        return m;
    }

    /** SMBIOS kasa tipi → chassis_type.code (DESKTOP / LAPTOP / MINI_PC / ALL_IN_ONE); kod yoksa pile bakar. */
    private static String chassis(String code, String battery) {
        if (code != null) {
            switch (code) {
                case "8", "9", "10", "14", "30", "31", "32" -> { return "LAPTOP"; }
                case "35", "36" -> { return "MINI_PC"; }
                case "13" -> { return "ALL_IN_ONE"; }
                case "3", "4", "5", "6", "7", "15", "16" -> { return "DESKTOP"; }
                default -> { }
            }
        }
        if (battery != null && !battery.equals("0")) {
            return "LAPTOP";
        }
        return code == null ? null : "DESKTOP";
    }

    /** JEDEC uretici kodu (orn. 802C0000802C) → marka; bilinmiyorsa ham deger. */
    private static String ramVendor(String raw) {
        String k = raw.toUpperCase();
        if (k.startsWith("802C") || k.contains("MICRON")) return "Micron";
        if (k.startsWith("80CE") || k.contains("SAMSUNG")) return "Samsung";
        if (k.startsWith("80AD") || k.contains("HYNIX")) return "SK hynix";
        if (k.startsWith("0198") || k.startsWith("8198") || k.contains("KINGSTON")) return "Kingston";
        if (k.startsWith("859B") || k.contains("CRUCIAL")) return "Crucial";
        if (k.startsWith("04CB") || k.contains("ADATA")) return "ADATA";
        if (k.startsWith("04CD") || k.contains("G.SKILL")) return "G.Skill";
        if (k.startsWith("029E") || k.contains("CORSAIR")) return "Corsair";
        return raw;
    }

    /**
     * Lookup tablosundan (os_family / chassis_type / cpu_vendor) code'a gore id;
     * yoksa sonraki id ile ekler. code null ise null.
     */
    private static String lookup(Connection c, String table, String code) throws SQLException {
        if (code == null || code.isBlank()) {
            return null;
        }
        String name = code.charAt(0) + code.substring(1).toLowerCase().replace('_', ' ');
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + table + " (id, code, name)"
                + " SELECT coalesce(max(id), 0) + 1, ?, ? FROM " + table + " ON CONFLICT (code) DO NOTHING")) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM " + table + " WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? String.valueOf(rs.getInt(1)) : null;
            }
        }
    }

    private static String dbVersion(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 'PostgreSQL ' || current_setting('server_version')");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static Long parse(String v) {
        try {
            return v == null ? null : Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Elle kayit / kontrol: bu makineyi machine tablosuna ekler (ya da bos alanlarini doldurur) ve id'yi basar. */
    public static void main(String[] args) throws Exception {
        DbConfig cfg = DbConfig.load();
        try (Connection c = java.sql.DriverManager.getConnection(cfg.url(), cfg.user(), cfg.password())) {
            detect().forEach((k, v) -> System.out.println(k + " = " + v));
            System.out.println("machine_id = " + resolve(c));
        }
    }
}
