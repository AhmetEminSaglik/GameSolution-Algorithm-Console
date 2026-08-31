import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import support.GameHarness;
import support.GameHarness.Result;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * "Golden master" / karakterizasyon testi.
 *
 * <p>Buradaki sayılar (12_400 / 1_023_656 / ...) matematiksel "doğru cevap"
 * OLARAK İDDİA EDİLMİYOR. Bunlar bugünkü kodun ÜRETTİĞİ değerler.
 * Testin işi: davranışı DEĞİŞTİRMEMESİ gereken bir refactor (enum'a geçiş,
 * cast temizliği, şişkin sınıfı bölme) bu değerlerden birini oynatırsa
 * <b>anında ve gürültülü şekilde patlamak</b>.
 *
 * <p>Sayının "doğru" olup olmadığını ayrı bir mekanizma kontrol ediyor:
 * {@link IndependentSolutionCountTest} (bağımsız brute-force) ve aşağıdaki
 * {@link #twoAlgorithms_agreeOnSolutionCount_5x5()} (iki bağımsız algoritmanın
 * aynı sayıda çözüm bulması).
 *
 * <p>dummyBackStep / totalBackStep algoritmaya ÖZGÜ verimlilik sayaçlarıdır;
 * iki algoritma için farklı olması NORMAL. Her biri kendi golden değerine sabitlenir.
 */
class GameRegressionTest {

    @AfterAll
    static void cleanup() {
        GameHarness.cleanGeneratedFiles(5);
    }

    @Test
    void firstSolution_5x5_behaviourIsUnchanged() {
        Result r = GameHarness.runRobot(5, GameHarness.FIRST_SOLUTION);
        assertEquals(12_400L, r.totalSolved(), "toplam cozum");
        assertEquals(4_809_736L, r.roundCounter(), "round counter (while dongu)");
        assertEquals(603_928L, r.dummyBackStep(), "dummy back step");
        assertEquals(2_404_856L, r.totalBackStep(), "total back step");
    }

    @Test
    void secondSolution_5x5_behaviourIsUnchanged() {
        Result r = GameHarness.runRobot(5, GameHarness.SECOND_SOLUTION);
        assertEquals(12_400L, r.totalSolved(), "toplam cozum");
        assertEquals(1_023_656L, r.roundCounter(), "round counter (while dongu)");
        assertEquals(83_076L, r.dummyBackStep(), "dummy back step");
        assertEquals(511_816L, r.totalBackStep(), "total back step");
    }

    /**
     * İki bağımsız algoritma aynı toplam çözüm sayısını bulmalı.
     * Bulmazlarsa en az birinde bug var — "doğru cevabı" bilmeden yakalanır.
     */
    @Test
    void twoAlgorithms_agreeOnSolutionCount_5x5() {
        long first = GameHarness.runRobot(5, GameHarness.FIRST_SOLUTION).totalSolved();
        long second = GameHarness.runRobot(5, GameHarness.SECOND_SOLUTION).totalSolved();
        assertEquals(first, second,
                "1. ve 2. algoritma 5x5'te ayni sayida cozum bulmali (bulmuyorsa birinde hata var)");
    }

    /** 6x6: yavaş (2. algoritma ~19 dk, 1. algoritma saatler). Normalde atlanır. */
    @Test
    @Tag("slow")
    void twoAlgorithms_agreeOnSolutionCount_6x6() {
        long first = GameHarness.runRobot(6, GameHarness.FIRST_SOLUTION).totalSolved();
        long second = GameHarness.runRobot(6, GameHarness.SECOND_SOLUTION).totalSolved();
        assertEquals(first, second, "6x6'da iki algoritma ayni sayida cozum bulmali");
        GameHarness.cleanGeneratedFiles(6);
    }
}
