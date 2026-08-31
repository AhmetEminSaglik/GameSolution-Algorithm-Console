import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import support.GameHarness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Bilmediğim bir sayının testini nasıl yazarım?" sorusunun cevabı.
 *
 * <p>Toplam çözüm sayısını (5x5 için 12_400, 6x6 için ~8.x milyon) önceden
 * BİLMİYORUZ — projenin amacı zaten bunu bulmak. Dolayısıyla
 * {@code assertEquals(dogruCevap, sonuc)} yazamayız; "doğru cevap" elimizde yok.
 *
 * <p>Yapılabilecek olan: aynı sayıyı <b>ikinci, bağımsız bir yöntemle</b> hesaplayıp
 * iki sonucun aynı çıkıp çıkmadığına bakmak. Burada bağımsız yöntem, oyunun
 * hareket kurallarını (8 sıçrama vektörü, tüm kareleri gez) sıfırdan uygulayan
 * ~15 satırlık saf bir DFS. Bu DFS, üretim algoritmalarının kodunu HİÇ kullanmıyor.
 *
 * <ul>
 *   <li>Brute-force == üretim algoritması  → iki bağımsız yöntem aynı sonuçta
 *       buluşuyor: sayının doğru olduğuna dair güçlü kanıt (ispat değil).</li>
 *   <li>Brute-force != üretim algoritması  → kesinlikle bir yerde bug var;
 *       hangi tarafta olduğunu ayrıca araştırırsın.</li>
 * </ul>
 *
 * <p>5x5 saniyeler sürer (CI'da çalışır). 6x6 brute-force saatler sürebilir →
 * {@code @Tag("slow")}, normal {@code mvn test}'te atlanır.
 */
class IndependentSolutionCountTest {

    /** Oyunun hareket vektörleri: N/S/E/W = ±3, çaprazlar = (±2,±2). Kaynak: src/game/location/direction/*.java */
    private static final int[][] MOVES = {
            {0, 3}, {0, -3}, {3, 0}, {-3, 0},
            {2, 2}, {-2, 2}, {2, -2}, {-2, -2}
    };

    @AfterAll
    static void cleanup() {
        GameHarness.cleanGeneratedFiles(5);
    }

    /** Tüm başlangıç karelerinden, tüm kareleri gezen (basit) yol sayısının toplamı. */
    private static long bruteForceCount(int edge) {
        long total = 0;
        boolean[][] visited = new boolean[edge][edge];
        for (int sx = 0; sx < edge; sx++) {
            for (int sy = 0; sy < edge; sy++) {
                visited[sx][sy] = true;
                total += dfs(sx, sy, 1, edge, visited);
                visited[sx][sy] = false;
            }
        }
        return total;
    }

    private static long dfs(int x, int y, int depth, int edge, boolean[][] visited) {
        if (depth == edge * edge) {
            return 1;
        }
        long count = 0;
        for (int[] m : MOVES) {
            int nx = x + m[0];
            int ny = y + m[1];
            if (nx < 0 || ny < 0 || nx >= edge || ny >= edge || visited[nx][ny]) {
                continue;
            }
            visited[nx][ny] = true;
            count += dfs(nx, ny, depth + 1, edge, visited);
            visited[nx][ny] = false;
        }
        return count;
    }

    @Test
    void bruteForce_matches_bothProductionAlgorithms_on5x5() {
        long brute = bruteForceCount(5);
        System.out.println("[oracle] 5x5 brute-force toplam yol sayisi = " + brute);

        long first = GameHarness.runRobot(5, GameHarness.FIRST_SOLUTION).totalSolved();
        long second = GameHarness.runRobot(5, GameHarness.SECOND_SOLUTION).totalSolved();

        assertEquals(brute, first, "1. algoritma bagimsiz brute-force ile ayni sonucu vermeli");
        assertEquals(brute, second, "2. algoritma bagimsiz brute-force ile ayni sonucu vermeli");
    }

    @Test
    @Tag("slow")
    void bruteForce_matches_bothProductionAlgorithms_on6x6() {
        long brute = bruteForceCount(6);
        System.out.println("[oracle] 6x6 brute-force toplam yol sayisi = " + brute);
        assertTrue(brute > 0);

        assertEquals(brute, GameHarness.runRobot(6, GameHarness.FIRST_SOLUTION).totalSolved());
        assertEquals(brute, GameHarness.runRobot(6, GameHarness.SECOND_SOLUTION).totalSolved());
        GameHarness.cleanGeneratedFiles(6);
    }
}
