# Gereken Düzenlemeler 2 — Puanları Yükseltme Yol Haritası

> Bu belge `proje-degerlendirme.md` §7'nin devamı. Soru: **"neleri, nasıl
> yaparsam puanlar artar?"** Aşağıda her kriter için (a) neden şu an tavana
> vurdu, (b) hangi somut adımlar, (c) adım sonrası beklenen puan, (d) efor.
> Madde kodları (`[A1]`, `[N2]`, `[H3]`...) `yapilmasi-gereken-duzeltmeler.md`
> ile aynı; oradaki detayı tekrar etmiyorum, buraya **o belgede olmayan yeni
> işleri** de ekliyorum (regresyon testi, JaCoCo, CI, benchmark, formatter).

## Nerede duruyoruz

| Kriter | Şu an | Hedef | Farkı kapatan ana iş |
|---|---|---|---|
| Mimari / Tasarım | 6.5 | **8.5** | enum refactor + cast temizliği + şişkin sınıf bölme |
| Çözüm Yöntemi (algoritma) | 7.5 | **8.5** | hot-path allocation temizliği + benchmark + karmaşıklık analizi |
| Kodlama Kalitesi | 6 | **8.5** | yazım hataları + magic number + custom exception |
| Clean Code | 6.5 | **9** | tek dil + isimlendirme + formatter/CI + kalan ölü kod |
| Test Edilebilirlik | 3.5 | **8** | JUnit + regresyon ağı + %70 pure-logic coverage + CI |
| **Ortalama** | **~6.0** | **~8.5** | |

**Puanı en çok oynatan tek şey: test.** 3.5 → 8 tek başına ortalamayı
~0.9 çeker. Ve test ağı kurulmadan mimari refactor'ler (enum, cast, SRP)
riskli — o yüzden sıra: **önce test, sonra mimari.**

10 neden değil: bu proje için 10 = mutation testing + property-based test +
ADR'ler + doc sitesi. Tek kişilik öğrenme projesinde 8.5 zaten "profesyonel
kalite" demek; 9-10 aşırı yatırım.

---

## 1. Test Edilebilirlik: 3.5 → 8  (EN ÖNCELİKLİ)

**Neden 3.5:** Tek satır otomatik test yok. `Main`/`BuildGame`/
`SafeScannerInput` `Scanner(System.in)`'e gömülü, sonuç `System.out`'a
gidiyor → programı kod içinden çalıştırmak zor.

### 1.1 Altyapı (yarım gün)
- `pom.xml`'e JUnit 5 (`junit-jupiter`) + AssertJ ekle.
- Test kaynağı: `pom.xml`'e `<testSourceDirectory>test</testSourceDirectory>`
  (proje `src/main/java` düzeninde değil) ya da düzeni standarda taşı.
- `maven-surefire-plugin` güncel sürüm.

### 1.2 Regresyon ağı — EN DEĞERLİ TEK TEST (yarım gün)
Mevcut davranışı **kilitleyen** karakterizasyon testi. Her mimari refactor
bundan sonra güvenli olur:
```
5x5, 2. çözüm  -> totalSolved == 12_400
               -> roundCounter == 1_023_656
               -> dummyBackStep == 83_076
               -> totalBackStep == 511_816
5x5, 1. çözüm  -> totalSolved == 12_400
               -> roundCounter == 4_809_736
               -> dummyBackStep == 603_928
```
- Önce **`Main`'i test edilebilir yap:** `Player runSolvedGame(int edge,
  BaseSolution solution)` gibi bir metot çıkar — `Scanner`/menü olmadan
  oyunu koşturup sonucu (`Score`) döndürsün. `main()` sadece bu metodu
  menüden besleyen ince kabuk olsun.
- 6x6 için de bir `@Tag("slow")` testi (CI'da ayrı profil).

### 1.3 Saf mantık birim testleri (2 gün) — yüksek ROI, sıfır I/O
| Sınıf | Test edilecek |
|---|---|
| `Validation` | `validateSquareNumbers` (4/5 sınırı), `calculateValidOrNot` (her yön için sınır içi/dışı), `isStepValueAvailable` |
| `Model` | `getTotalSquareCount` |
| `CalculationDeadlyPoint` | `calculateDeadlyPoint` — tablo `sozde-kod.md` §7'de hazır |
| `WeightOfAvailableWay` | ağırlık dizisi `[8,7,6,5,4,3,2,1]` |
| `StringFormat` | `converNumberToReadableNumbers`: 1234567→"1_234_567", 0, negatif, <1000 |
| `ConvertNanoTimeToTime` | ms → h:m:s:ms dönüşümü |
| `BaseSolution` | `getSolutionFileName` — eski eval'deki "setGame/setSolution sırası" bug'ını yakalayan test |
| `Score` | sayaç artış + lock/unlock semantiği |
| `Compass`/`DirectionCompass`/`KeyboardCompass` | id eşlemeleri |
| `SwitchDirection` | her compass değeri → doğru yön; geçersizde `null` |
| `BuildGame` | doğru boyutta `int[][]` / `boolean[][]` |
| `CheckSquare` | elle kurulmuş küçük tahtada free/visited mantığı |

### 1.4 Coverage + CI (yarım gün)
- JaCoCo plugin; non-UI paketlerde **%70 satır** eşiği, altına düşerse
  `mvn verify` fail.
- GitHub Actions: `mvn -B verify` her push'ta (`.github/workflows/ci.yml`).

**Sonrası: 8/10.** (10 için mutation testing / PBT gerekir.)
**Efor: ~3-4 gün.**

---

## 2. Kodlama Kalitesi: 6 → 8.5

**Neden 6:** ~72 yazım hatası eşleşmesi (26 dosya), çoğu magic number hâlâ
çıplak, `ErrorMessage.throwError` genel `Exception` fırlatıyor, 4 yerde
`catch (Exception)`, sıcak yolda `assert`, `-1` sentinel dönüşler.

### 2.1 Yazım hataları — `[N1]`–`[N6]` (yarım gün, IntelliJ `Shift+F6`)
- **Sınıf:** `SelectFirstSqaureToStart`→`...Square...`,
  `InpectingForwardLocation`→`Inspecting...`, `EasylyReadNumber`→`Easily...`,
  `SealationOfLocation`→`SealingOfLocation`.
- **Metot:** `prepareation`, `converNumberToReadableNumbers`→`convert...`,
  `StringToInt`→`stringToInt`, `...ByExcatlyLocation`→`...Exactly...`,
  `fillCordinates`→`fillCoordinates`, `updateExistSituation`→`updateExitSituation`,
  `...RunHealtyThe...`→`...Healthy...`, `...AppendCondtion`→`...Condition`.
- **Kullanıcıya görünen string:** `"Unknow choice"`, `"Unknow Option"`,
  `"occured"`, `"Stuation"`.
- 3. çözüm sınıf adı (`ThirdtSolution_GoldenSquare`) — o pakete dokunma
  kararı sürüyorsa **not düş, sonraya**.

### 2.2 Magic number → isimli sabit — `[M2]`–`[M8]` (yarım gün)
Her birine 1 satır "neden bu değer" yorumu:
`MIN_SQUARE_EDGE = 4`, `SECOND_ONE_WAY_MEANS_EXIT = 2`,
`MAX_ONE_WAY_BEFORE_ABORT = 3`, `FIRST_STEP = 1`, `NO_DIRECTION_ID = -1`.
(`ORTHOGONAL_STEP = 3` / `DIAGONAL_STEP = 2` → `[A1]` ile birlikte.)

### 2.3 Hata yönetimi — `[X1]`–`[X8]` (1-1.5 gün)
- `class InvalidGameConfigException extends Exception` — `ErrorMessage.throwError`
  ve `SquareValidationGame` bunu kullansın; çağıran artık `catch (Exception)`
  yazmak zorunda kalmaz.
- 4 `catch (Exception)` → beklenen spesifik tipe daralt (`NullPointerException`,
  `ArrayIndexOutOfBoundsException`).
- `Robot.updateVisitedDirection`'daki `assert` → gerçek `if (...) throw` ya da
  `Trace`.
- `ConvertVariable.StringToInt` `-1` sentinel → `OptionalInt` ya da exception.
- Kalan 2 `printStackTrace` → `ErrorMessage` / `Trace`.

### 2.4 Encapsulation — `[E1]`–`[E7]` (yarım gün)
7 public mutable alan (`BaseSolution.playerLocation`, `Player.gameRule`,
`Score.lockedCounterOfMovingBackLose`, `Move.game`,
`Move.updateValuesInGameModel`, `BaseControlInput.game`,
`ComparisonOfSolutions.copyModel`) → `private`/`protected` + getter.

### 2.5 Derleyici sıkılaştırma (yarım gün)
- `maven-compiler-plugin`: `<compilerArgs><arg>-Xlint:all</arg></compilerArgs>`,
  uyarıları temizle.
- Opsiyonel: Error Prone (`com.google.errorprone`) Maven'e ekle.

**Sonrası: 8.5/10.**
**Efor: ~3 gün.**

---

## 3. Clean Code: 6.5 → 9

**Neden 6.5:** yazım hataları okunabilirliği kırıyor, yorumlar TR/EN karışık,
formatlama IDE ayarına bağlı (`.editorconfig` yok), `nbproject/`+`build.xml`
artık ölü ama duruyor.

### 3.1 Tek dil — `[N6]` (1 gün)
Karar: **İngilizce identifier + kısa İngilizce yorum.** Algoritma anlatımı
Türkçe kalabilir ama `sozde-kod.md` içinde. Kod tabanındaki karışık yorumları
tek dile çevir.

### 3.2 Formatlama + CI (yarım gün)
- `.editorconfig` (indent, `charset=utf-8`, `eol=lf`, trim trailing) — `[C2]`.
- `.gitattributes`: `*.java text eol=lf` + bir kerelik normalizasyon — `[C1]`.
- `spotless-maven-plugin` (veya `fmt-maven-plugin`) → `mvn spotless:check`
  CI'da; `spotless:apply` bir kez.

### 3.3 Kalan ölü kod / boş metot — `[L5]`/`[L6]`/`[L8]`
- `PlayGame.printToFile` / `appendFileSolutionName` boş gövde: ya
  `if (Trace.ENABLED) { gerçek impl }` ya sil.
- `printGamelastStuation` yorumlu blokları: "kod var elleme" notu duruyorsa
  kalsın; ama temizi → `Trace`-guard'lı gerçek metoda çevirmek.
- `[L8]` kalan debug `System.out` → `Trace` (Location.printLocation, PrintArray.*).

### 3.4 Yapı sadeleştirme
- `nbproject/` + `build.xml` + kök `manifest.mf` sil (Maven artık otoriter) —
  `[C5]`. Ya da `legacy/`'ye taşı.
- Kök dizindeki `.txt`/`.png` "taktik" dosyaları → `docs/` — `[C4]`.
- Her pakete `package-info.java` (2 satır amaç) ya da `docs/architecture.md`.

**Sonrası: 9/10.**
**Efor: ~2 gün.**

---

## 4. Mimari / Tasarım: 6.5 → 8.5

**Neden 6.5:** 12 `(Robot)` cast soyutlamayı deliyor, `MathFunctionForSecondSolution`
tek başına 5 iş yapıyor (~182 satır), 8 yön ayrı sınıf, `SelectFirstSqaureToStart`
bir "yön" olmadığı halde `DirectionLocation`'dan türüyor.

> **Ön koşul: §1'deki regresyon ağı hazır olmalı.** Bu refactor'ler davranış
> değiştirmemeli; 12_400 vs. sayıları testte sabit kalmalı.

### 4.1 `[A1]` 8 yön sınıfı → `enum Direction` (2-3 gün, bayrak refactor)
```java
enum Direction {
    NORTH(0, 0, -3), NORTH_EAST(1, 2, -2), EAST(2, 3, 0), ... , BACK(8, 0, 0);
    final int id, dx, dy;
}
```
- Compass eşlemesi `EnumMap` ya da `ordinal()`.
- `SwitchDirection` 9'lu if → `switch`/lookup; `LocationsList` → `values()`.
- `[M1]` (±3/±2 magic) ve `[H1]` (allocation) kendiliğinden çözülür.
- Derleyici `switch` exhaustiveness kontrol eder.

### 4.2 `[A2]` `(Robot)` cast'lerini kaldır (1 gün)
2. çözüm sınıfları constructor'da bir kez `Robot`'a cast edip alan tutsun,
ya da `RoadMemory`/`RobotMemory` erişimi `interface HasRobotMemory` arkasına
alınsın. `Player` gerçekten polymorphic olsun.

### 4.3 `[A3]` `MathFunctionForSecondSolution`'ı böl (2 gün)
`ForwardWayScanner`, `DeadlyPointCalculator` (zaten `CalculationDeadlyPoint`
var), `OneWayExitTracker`, `NavigationBuilder` — ana sınıf ~50 satırlık
orkestratöre insin. Taslak `sozde-kod.md` §10'da.

### 4.4 Küçük yapısal düzeltmeler (yarım gün)
- `[A4]` `SelectFirstSqaureToStart extends DirectionLocation` → kompozisyon
  ya da düz `int x, y`.
- `[A7]` kullanılmayan `Game(Model, Player)` constructor sil.
- `[A8]` `TimeKeeper`'ı `...robot` paketinden `game.time`'a taşı (Person da
  kullanıyor).
- `[A9]` `Player.clearVisitedDirections` → `game.getPlayer().getCompass()`
  yerine `this.getCompass()`.

### 4.5 Opsiyonel — bağımlılık yönetimi
Her yerde `new X(game)` yerine bir `GameContext` / basit DI. Büyük iş,
en sona.

**Sonrası: 8.5/10.**
**Efor: ~1 hafta.**

---

## 5. Çözüm Yöntemi (algoritma): 7.5 → 8.5

**Neden 7.5:** algoritmik ilerleme gerçek ama (a) karmaşıklık analizi yok,
(b) benchmark harness yok, (c) sıcak yolda tur başına gereksiz allocation var
(hız senin açık hedefin), (d) "iki çözümü karşılaştır" özelliği yarım.

### 5.1 Hot-path allocation temizliği — `[H1]`–`[H6]` (2 gün)
- `LocationsList...getListOfLocationsAccordingToPlayerCompass` her çağrıda
  `new ArrayList` + 9 nesne → compass başına bir kez cache'le.
- `InpectingForwardLocation...` `ArrayList` döndürüp sadece `.size()`
  kullanılıyor → doğrudan `int` döndür (`[H2]`).
- `new CalculationDeadlyPoint(game)` her adımda → tek örnek / statik (`[H3]`).
- `WeightOfAvailableWay` + dizi → `static final`.
- `GameModelProcess.calculateIndexOfGivenStepInGameSquareArrays` bulunca
  `return` (`break` bile yok) (`[H6]`).
- `[A1]` enum'u `[H1]`'i zaten çözer.

### 5.2 Benchmark harness (1 gün)
- `yapilmasi-gereken-duzeltmeler.md` §2.5'teki `ResultSink` arayüzü:
  `FileResultSink` / `NullResultSink` (saf algoritma hızı) / `DbBatchResultSink`.
- `TimeKeeper`'a pause/resume kronometre → `algorithmElapsed` vs `totalElapsed`
  vs `sinkTime`.
- `BENCHMARKS.md`: 5x5 / 6x6 / 7x7 için her 3 çözümün round counter + süre.
- Opsiyonel: ayrı JMH modülü.

### 5.3 Karmaşıklık + problem tanımı (yarım gün)
- `sozde-kod.md`'ye her çözüm için zaman/yer karmaşıklığı, 2.'nin 1.'yi neden
  yendiği, 3.'nün "golden square"inin ne kazandırdığı.
- `README.md`: çözülen problem tam olarak nedir (her kareye uğra, değerler,
  kenar > 4 vs.).

### 5.4 `ComparisonOfSolutions`/`CopyModel` kararı
Ya "iki çözümü karşılaştır" özelliğini düzgün geri bağla, ya iki sınıfı sil.
Şu an limboda (`[D16]`).

**Sonrası: 8.5/10.**
**Efor: ~3-4 gün.**

---

## 6. Önerilen sıra — kilometre taşları ve puan projeksiyonu

| # | İş | Süre | Sonrası ortalama |
|---|---|---|---|
| **M0** | §1.1–1.3: JUnit + regresyon ağı + saf mantık testleri | ~3 gün | ~6.6 |
| **M1** | §2.1–2.2 + §3.2 + §2.4: yazım, magic number, editorconfig, encapsulation (hepsi mekanik/IDE) | ~2 gün | ~7.2 |
| **M2** | §2.3 + §3.1 + §3.4: custom exception, catch daraltma, tek dil, nbproject temizliği | ~2 gün | ~7.6 |
| **M3** | §4: enum + cast + SRP bölme + küçük yapısal | ~1 hafta | ~8.0 |
| **M4** | §5 + §1.4: hot-path, benchmark, karmaşıklık dok., coverage %70 + CI | ~4 gün | ~8.5 |

**Sadece 3 şey yapacaksan:**
1. **M0** — regresyon ağı (her şeyin önünü açar, mimariyi güvenli yapar).
2. **M1** — bir günde okunabilirlik + kod kalitesi belirgin sıçrar.
3. **§4.1 enum refactor** — mimaride tek en büyük hamle.

---

## 7. Puanı yükseltmeyen ama "yapılmış" hissi veren şeyler (tuzak)

- Yorum eklemek ≠ clean code. Kötü isim + açıklayıcı yorum yerine iyi isim.
- Getter/setter üretmek ≠ encapsulation, eğer alan yine her yerden set
  ediliyorsa. Invariant'ı sınıf korumalı.
- `try/catch` eklemek ≠ hata yönetimi, eğer catch loglayıp yutuyorsa.
- 1 tane "her şeyi çalıştıran" test ≠ test coverage; ama M0'daki regresyon
  ağı + saf birim testleri gerçek değer.
