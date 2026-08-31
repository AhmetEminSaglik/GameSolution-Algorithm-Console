# Yapılması Gereken Düzeltmeler — Temiz Kod Eylem Listesi

> Bu belge, `proje-degerlendirme.md` üzerine kurulu **uygulanabilir bir yapılacaklar
> listesidir**. `proje-degerlendirme.md` "neden sıkıntılı" sorusunu anlatıyordu;
> bu belge "tam olarak hangi dosyada ne yapılacak" sorusunu madde madde cevaplıyor.

## Bu belge nasıl kullanılır

- **Kodun çalışma mantığı bozulmayacak.** Her madde, davranışı birebir koruyacak
  şekilde (refactor) yazıldı. Davranışı etkileme ihtimali olan maddeler
  **Öncelik 1**'de toplandı ve tek tek "neden güvenli" notu içeriyor.
- **3. çözüm (`solution/third/*`, `move/**/thirdsolution/*`, `memory/GraphMemory`)
  bu turda es geçiliyor.** O paketlere dokunulmayacak; sadece 1. ve 2. çözüm +
  ortak altyapı kapsamda.
- Her madde bağımsız uygulanabilir. Madde kodları (`[R1]`, `[D2]`, ...) düzeltme
  aşamasında referans için.
- Bir madde bitince başındaki `[ ]` → `[x]` yapılacak.
- **Yorum silme kuralı (kullanıcı notu):** Yorum satırı haline getirilmiş **kod**
  silinebilir. Ama **davranışı/niyeti açıklayan düz yazı yorumlar KORUNUR**
  (ör. `MoveBack`'teki "Ozel RoundCounter : geri adim atmaya baslandiktan sonra..."
  paragrafı). Ayrıca `printGamelastStuation` içeren yorumlu bloklara
  (`PlayGame.java`, `MoveBack.java`) **dokunulmaz** — kullanıcı "kod var, elleme" dedi.

---

## 0. `proje-degerlendirme.md`'den bu yana ne değişti

Son birkaç commit'te (`2859065`, `dfdc349`, `d2f4530`, `124f3f1`) yapılan küçük
değişiklikler ve bunların getirdiği **yeni sorunlar**:

| Değişiklik | Sonuç / yeni sorun |
|---|---|
| `Robot.getCompass()` artık `new DirectionCompass()` yerine `compass` alanını döndürüyor | ✅ Doğru yön. Aynısı `Person.getCompass()` için de yapılmalı (bkz. `[P3]`). |
| `Score`: `counterOfMovingBackLose` → `counterOfDummyBackMove` olarak yeniden adlandırıldı, ayrıca `counterTotalBackStep` eklendi | ⚠️ `counterTotalBackStep` **hiçbir yerde artırılmıyor** — tek çağrı noktası `UpdateForMovedBack.updatePlayerStepValue()` içinde yorum satırı. Sonuç: `PlayGame`'in bastığı `"Total Back Step : ..."` satırı **her zaman 0**. (bkz. `[R4]`) |
| `PlayGame.saveGameResultToScore()` içindeki `appendFileTotalSolvedValue()` çağrısı **yorumdan çıkarıldı** (aktif edildi) | ⚠️ Bu metot `((Robot) player)` cast'i yapıyor → **Person oynarken `ClassCastException`**. `proje-degerlendirme.md` §2.1 tam olarak bunu öngörmüştü. (bkz. `[R1]`) |
| `MoveBack.updateVisitedDirection()`: sabit `100` yerine `Math.pow(edge, 2)` kullanılıyor | ⚠️ Doğru fikir (5x5/6x6 fark etmeksizin çalışsın) ama `Math.pow` **double döndürüyor ve en sıcak döngüde her geri adımda çağrılıyor**; `int == double` karşılaştırması da kırılgan. (bkz. `[R2]`) |
| `RobotGameOver`: `checkSquare` alan oldu; `isRobotFinishedFirstSquare()` metodu eklendi | ⚠️ `isRobotFinishedFirstSquare()` **ölü** — sadece yorum içinde referansı var. `isGameOver` içinde de yorumlanmış satır kaldı. (bkz. `[D8]`) |
| `PlayGame` log satırları yeniden düzenlendi ("Total Step", "Total Dummy Step" vb.) | ⚠️ `System.out` üzerinden loglama hâlâ dağınık; test/prod ayrımı yok (bkz. **Bölüm 2**). Ayrıca `appendFileTotalSolvedValue()` içinde `calculatePlayerTotalWinScore()` **ikinci kez** çağrılıyor. (bkz. `[R3]`) |
| `sozde-kod.md` eklendi | ✅ 2. çözümün sözde kodu — refactor sırasında referans olarak kullanılabilir. |

---

## 1. Öncelik 1 — Davranışı etkileyebilecek / riskli temizlikler

Bunlar "temiz kod" olduğu kadar **bug** da; en önce bunlar.

> **DURUM: Bölüm 1 tamamlandı (2026-08-31).** Tüm değişiklikler `javac 21` ile
> temiz derlendi; 2. çözüm 5x5 çalıştırıldı ve doğrulandı:
> `total Solved : 12_400`, `Round Counter : 1_023_656`, `Total Dummy Step : 83_076`
> — hepsi commit'lenmiş `Solution-2-5x5_Completed.txt` ile **birebir aynı**.
> Mevcut metriklerin hiçbirine dokunulmadı.

- [x] **`[R1]` `PlayGame.appendFileTotalSolvedValue()` içindeki `(Robot) player` cast'i kaldırılacak.** ✅
  - `Player`'a soyut `getSolutionName()` eklendi; `Robot` → çözüm sınıfı adı,
    `Person` → `"Person"`. `PlayGame.java:139` cast'i + kullanılmayan `Robot` import'u kalktı.
  - Yer: `src/game/play/PlayGame.java:139` (`text += "\nSolution :" + ((Robot) player).getSolution()...`)
  - Sorun: `saveGameResultToScore()` (satır 127) artık bu metodu koşulsuz çağırıyor;
    Person oynarsa `ClassCastException`.
  - Çözüm: `Player`'a soyut `String getResultLabel()` (veya `getSolutionName()`) ekle;
    `Robot` → `getSolution().getClass().getSimpleName()` döndürür,
    `Person` → `"Person"` döndürür. Cast tamamen kalkar.
  - Neden güvenli: Robot yolunda üretilen string birebir aynı kalır.

- [x] **`[R2]` `MoveBack.updateVisitedDirection()` içindeki `Math.pow` sıcak yoldan çıkarılacak.** ✅
  - `Model.getTotalSquareCount()` (kenar*kenar) eklendi. `Math.pow(edge, 2)` çağrıları
    şu 4 yerde bununla değişti: `MoveBack.updateVisitedDirection`,
    `PlayGame.calculatePlayerTotalWinScore`, `Validation.isStepValueAvailable`,
    `Player.clearVisitedDirections`. Tamsayı matematiği birebir aynı, `double` üretimi kalktı.

- [x] **`[R3]` `PlayGame.appendFileTotalSolvedValue()` içindeki fazladan `calculatePlayerTotalWinScore()` çağrısı kaldırıldı.** ✅
  - Neden güvenli: Oyun daima `step == 1` iken bitiyor (`RobotGameOver.allDirectionsAreVisitedAtStep1`),
    dolayısıyla bu noktada `calculatePlayerTotalWinScore()`'un `if (step == totalSquareCount)`
    şartı zaten hiç tutmuyordu → çağrı **no-op**'tu. Sayaçlara etkisi yok (test'te
    `total Solved` ve `Total Dummy Step` değişmedi).

- [x] **`[R4]` "Total Back Step" gerçekten sayılıyor.** ✅ (Kullanıcı yaptı.)
  - `UpdateForMovedBack.updatePlayerStepValue()` içinde
    `game.getPlayer().getScore().increaseCounterTotalBackStep();` satırı açıldı.
  - Test koşusunda `Total Back Step : 511_816` üretti (önceden 0'dı). **Bu metriğe
    ve diğer tüm metriklere (dummy step dâhil) dokunulmadı.**

- [x] **`[R5]` `WeightOfAvailableWay` dizi taşması güvene alındı.** ✅
  - Araştırma sonucu: `availableWayNumber` pratikte en fazla 7 — incelenen komşu
    karenin, robotun üzerinde durduğu (ziyaret edilmiş) kare her zaman bir
    komşusudur, o yön kapalıdır. Yani indeks 8 asla okunmaz.
  - Yine de dizi `MAX_FORWARD_WAYS + 1` (=9) boyutuna çıkarıldı; ağırlık formülü
    `MAX_FORWARD_WAYS - i` (=`8 - i`) olarak sabitlendi → indeks 0..7 değerleri
    **birebir aynı** ([8,7,6,5,4,3,2,1]), indeks 8 sadece asla-tetiklenmeyen güvenlik
    yuvası. Gerekçe kod içine yorum olarak yazıldı.

- [x] **`[R6]` `SwitchDirection.choseDirection()` `null` dönüşü — kısmen ele alındı.** ⚠️
  - **Yapıldı:** Korumasız tek çağıran (`CheckSquare.isSquareFreeFromVisitedArea`)
    `location != null` guard'ı aldı → belgelenen "gizli NPE" yolu kapandı.
    `choseDirection`'a null sözleşmesini + "enum'a taşınınca kalkacak" TODO'su yazıldı.
  - **Yapılmadı (bilerek):** "null yerine `IllegalArgumentException` fırlat" kısmı
    **ertelendi**. `getLocationFromCompass` çağrısı `Validation.isInputValidForArray`
    içinde `try` bloğunun **dışında**; fırlatırsak Person geçersiz sayı girince
    "tekrar sor" davranışı yerine **çöker**. Bu kısım Bölüm 2 (`Trace`) + `[A1]`
    (yön enum'u) ile birlikte gelmeli.

- [x] **`[R7]` `MathFunctionForSecondSolution.calculateFunctionResult()` boş `catch` kaldırıldı.** ✅
  - `try { ... navigationService.getCompulsoryLocation(...) ... } catch (Exception e) {}`
    → `DirectionLocation c = navigation.getCompulsoryLocation(); if (c != null) { ... return ...; }`.
    Davranış birebir: non-null → o yönü kullan/dön; null → hesaplamaya devam.
  - Not: `NavigationService.getCompulsoryLocation()` (exception fırlatan sürüm)
    **silinmedi** — hâlâ 3. çözüm (`MathFunctionWithSpecialFeaturesForThirdSolution`)
    kullanıyor, o paket kapsam dışı.

---

## 2. Loglama — test / prod ayrımı  ✅ (temel kısım yapıldı 2026-08-31)

**Amaç:** Rekor denemesi (prod) yaparken **sıfır loglama maliyeti**; hata ayıklarken
(test) ayrıntılı iz. Milyar kez çalışan döngüde `System.out` / string birleştirme
kabul edilemez.

### 2.1 "if'e 1 trilyon kez girmek yavaşlatmaz mı?" — hayır, çünkü `if` derlenmiş kodda YOK

Kritik nokta: `Trace.ENABLED` bir **derleme-zamanı sabiti** (`public static final
boolean ENABLED = false;` — sağ tarafı literal). Java Dil Şartnamesi (JLS §14.21,
"unreachable statements") gereği `javac`, `if (Trace.ENABLED) { ... }` bloğunu
`ENABLED == false` iken **bytecode'a hiç koymaz**. Java'da "conditional compilation"
tam olarak böyle yapılır.

Yani:

| Durum | Ne oluyor |
|---|---|
| `ENABLED = false` + `if (Trace.ENABLED) Trace.log("... " + x)` | `javac` bu satırı **komple siler**: ne `if`, ne string birleştirme, ne metot çağrısı bytecode'da olur. "1 trilyon kez `if(false)`" diye bir şey **yok** — kod derlenmiş halde mevcut değil. Fark: **tam sıfır.** |
| `ENABLED` **compile-time sabiti değilse** (config'ten okunuyor, `final` değil) | `if` bytecode'da kalır. 1 trilyon kez çalışır. Dal her zaman `false` → CPU dal tahmincisi %100 bilir, ~0.3 ns/tur → ~milisaniyeler-saniye toplamda. Ayrıca JIT ölü dalı silemez, komut önbelleğini şişirir. **İşte bu senaryodan kaçınıyoruz.** |

**Kural:** guard'ı **çağrı yerinde** de koy:
```java
if (Trace.ENABLED) Trace.log("step " + step + " dir " + dir);   // DOĞRU: her şey elenir
```
Sadece `log()` içindeki guard'a güvenirsen, `"step " + step + ...` string'i her turda
**yine üretilir** (asıl pahalı kısım I/O değil, string birleştirmedir); JIT ısındıktan
sonra bunu *belki* eler ama garanti değil.

**Doğrulama:** `javac` sonrası `javap -c -p <çağrı yapan sınıf>` → `ENABLED=false` iken
blok bytecode'da görünmez.

**Uyarı (constant inlining):** `ENABLED`'ı `false` yapıp *sadece* `Trace.java`'yı
derlersen, eski değeri gömmüş öbür sınıflar değişikliği görmez. **Her zaman tüm
`src`'i yeniden derle.**

### 2.2 `System.out` mu, `java.util.logging` mi, SLF4J/Logback mı?

**Bu proje için karar: `Trace` + `System.out` (yapıldı).** Gerekçe:

| Seçenek | Ne zaman mantıklı | Bu projede |
|---|---|---|
| **Compile-time `Trace` + `System.out`** | Prod'da **kesinlikle sıfır** maliyet şartı varsa; bağımlılık istemiyorsan; log operasyonel değil, hata-ayıklama aracıysa | ✅ **Bunu kullanıyoruz.** Tek sabit + tüm src derle. Prod'da iz kodu fiziksel olarak yok. |
| **`java.util.logging` (JUL)** | JDK içi, bağımlılık yok, seviye (FINE/WARNING/SEVERE) istiyorsan; ama `logger.fine(x)` çağrısında argüman yine hesaplanır → `if (logger.isLoggable(FINE))` guard'ı gerekir ve bu **runtime** kontrolü, derleyici silmez | ❌ Runtime guard maliyeti + kurulum hantallığı; "tam sıfır" veremez. |
| **SLF4J + Logback / Log4j2** | Uzun ömürlü servis; runtime'da seviye değiştirme; rolling file; **async appender** (I/O'yu döngüden çıkarır); JSON/yapılandırılmış log; korelasyon ID; Spring vb. entegrasyonu | ❌ Bağımlılık + build sistemi (`[A6]`) gerektirir; senin duruşun "prod'da log yok" → async'e bile gerek yok. Uygulama servise dönüşürse tekrar değerlendir. |

**Her framework için değişmeyen sıcak-döngü kuralı:** milyar turluk döngüde
logger'ı **asla guard'sız çağırma**. Ya derleme-zamanı ele (Trace deyimi) ya
`if (logger.isLoggableX())` ile sar. String kurmak öldürür, I/O değil (zaten hiç olmuyor).

### 2.3 Yapılanlar (2026-08-31)

- [x] **`[L1]` `trace.Trace` sınıfı oluşturuldu.** `src/trace/Trace.java` —
  `public static final boolean ENABLED = false`, `log(msg)` / `log(tag, value)`,
  ikisi de içeride `if (ENABLED)` guard'lı; sınıf javadoc'unda yukarıdaki
  "neden sıfır maliyet" açıklaması + `javap` doğrulama notu var.
- [x] **`[L4]` TÜM JOptionPane / `ShowPanel` kaldırıldı.**
  - `src/errormessage/joptionpanel/` paketi + `ShowPanel.java` **silindi**.
  - 19 dosyadan `import errormessage.joptionpanel.ShowPanel;` ve tüm yorum-içi
    `// ShowPanel.show(...)` satırları temizlendi.
  - 2 aktif çağrı `ErrorMessage.appearWarnings(...)`'a çevrildi:
    `Move.changeStartLocationSpecialMovement` ("Y siniri asti"),
    `SealationOfLocation` catch bloğu (index taşması — `printStackTrace` de kaldırıldı,
    detay mesaja gömüldü).
  - `UpdateForMovedBack`'ten artık kullanılmayan `import javax.swing.*;` ve
    `import sleep.Sleep;` kaldırıldı.
- [x] **`[L7]` `sleep` paketi silindi.** `src/sleep/Sleep.java` hiç kullanılmıyordu.
- [x] **`[L3]` (kısmi) birkaç debug `System.out` → `Trace`:**
  `Main.selectPlayer` (`"game : ..."`), `CopyModel` tanılama satırları (4 adet).
  - **Kasıtlı bırakılanlar:** menü/prompt çıktıları (`Main`, `BuildGame`,
    `SafeScannerInput`) ve **sonuç çıktıları** (`"All Solutions are DONE"`,
    `PlayGame`'deki `"Elapsed time"`, `"Total Back Step"`, `"Total Step"`,
    `"Total Number Solved"`). Bunlar loglama değil, **programın ürünü** — prod'da
    da görünmeli, döngü dışında oldukları için maliyetsiz. (`[L5]`)

### 2.4 Kalanlar (sonraki tur)

- [ ] **`[L5]` `print` paketini kavramsal olarak "sonuç yazımı" diye netleştir.**
  `FileWriteProcess` sonuç dosyalarını (`*_Completed.txt`,
  `*_EverySingleSquareTotalValue.txt`) yazıyor — bunlar **ürün**, prod'da da yazılır.
  - **Önemli teknik borç:** `FileWriteProcess.append()` her çağrıda dosyayı
    `open`+`close` yapıyor. Bugün sıcak yolda değil (oyun sonu). DB'ye / adım-adım
    ize geçilirse buffer/bağlantı **açık tutulmalı** (bkz. §2.5).
- [ ] **`[L6]` `PlayGame.printToFile` / `appendFileSolutionName` boş gövdeli metotlar.**
  - "Test modunda son durumu bas" isteniyorsa gövde `if (Trace.ENABLED) { ... }`
    ile gerçek implementasyona kavuşsun; istenmiyorsa sil.
  - **`printGamelastStuation` içindeki yorumlu bloğa DOKUNMA** (kullanıcı notu:
    "kod var, elleme"). Aynısı `MoveBack.java`'daki yorumlu `printGamelastStuation`
    kopyası için de geçerli.
- [ ] **`[L8]` Kalan debug `System.out`'ları da `Trace`'e taşı** (isteğe bağlı):
  `Location.printLocation()`, `PrintArray.*` (bunlar zaten "yazdır" amaçlı util;
  çağıran karar versin — düşük öncelik).

### 2.5 DB'ye kaydetme — test / prod / persistence tasarımı (ileriye dönük not)

Soru: sonuçlar (ör. 10x10'da tüm kareler dolu bir çözüm haritası) artık txt yerine
**DB'ye batch-insert** edilecek. Ayrı bir mod mu (`test / prod / db_save`)? Yoksa
DB yazarken süreyi mi duraklatalım?

**Öneri — ayrı mod AÇMA. İki dik eksen var, karıştırma:**

1. **İz (trace) ekseni** — derleme-zamanı `Trace.ENABLED` (yukarıdaki). Tek karar.
2. **Sonuç hedefi (sink) ekseni** — sonuç *her zaman* bir yere yazılır (ürün bu):
   ```
   interface ResultSink {
       void accept(int[][] solvedMap);   // veya düz int[]/byte[] buffer
       void flush();
       void close();
   }
   ```
   - `FileResultSink`  → bugünkü txt davranışı
   - `DbBatchResultSink` → JDBC `addBatch()` + N'de bir `executeBatch()`
   - `NullResultSink`  → **saf algoritma hızını** ölçmek için (hiçbir şey yazmaz)

   Başlangıçta `Main`'de tek satır / tek sabitle seçilir. "3. mod" yok; sadece
   sink implementasyonu değişir.

**Süre ölçümü — "algoritma süresi" ile "duvar saati"ni ayır:**

`TimeKeeper`'a duraklatılabilir bir kronometre ekle:
```
stopwatch.start();
... çöz ...
// her flush öncesi:
stopwatch.pause();  jdbcBatch.executeBatch();  stopwatch.resume();
```
Rapor:
- `algorithmElapsed = totalElapsed - sinkTime`  → **rekor için baktığın sayı**
- `totalElapsed`  → dürüst duvar saati
- `sinkTime`  → persistence maliyeti (batch boyutu doğru mu anlarsın)

Bu tam olarak senin "hem total başlangıç-bitiş, hem batch insert aralığını çıkaran
bir toplam" fikrin — ve doğru yaklaşım bu.

**Batch insert pratiği:**
- Bellekte tampon (`List<int[][]>` ya da düz `int[]`); N'de bir flush (ör. 1_000–10_000).
  10x10 harita = 100 int; 10k batch ≈ birkaç MB — sorun değil.
- Tek `Connection`, `autoCommit = false`, batch başına `commit`.
- MySQL: `rewriteBatchedStatements=true`. Postgres: `COPY` en hızlısı.
- `PreparedStatement` + `addBatch()`/`executeBatch()`.

**İleri seviye (opsiyonel):** çözücü thread sadece üretir, ayrı bir thread kuyruktan
alıp batch-insert eder (producer/consumer). O zaman çözücü I/O için hiç durmaz;
`algorithmElapsed == totalElapsed` doğal olur. Maliyeti: karmaşıklık + backpressure.
Şimdilik pause/resume kronometre yeterli.

### 2.6 Özet kural

| Ne | Nereye |
|---|---|
| Döngü içi "buraya geldi", değişken dökümü | `if (Trace.ENABLED) Trace.log(...)` — prod'da fiziksel olarak silinir |
| Kullanıcı promptu / menü | `System.out` (kalır) |
| "Çözüldü", süre, toplam, harita — programın ürünü | `ResultSink` (File/Db/Null), döngü dışı; süre ölçümünde `pause/resume` |
| Modal uyarı (`JOptionPane`) | ✅ tamamen kaldırıldı |
| Gerçek uyarı / hata | `ErrorMessage.appearWarnings` / `appearFatalError` |

---
## 3. Öncelik 2 — Ölü kod ve yorum satırı haline getirilmiş kod

`git` geçmişi her şeyi tutuyor; bunlar okunabilirliği düşürüyor ve "acaba lazım mı"
sorusu sorduruyor.

- [ ] **`[D1]` Tamamen yorumlanmış sınıfları sil:**
  - `src/game/move/PersonMove.java` (dosyanın tamamı `/* ... */`)
  - `src/game/move/RobotMove.java` (dosyanın tamamı `/* ... */`)
  - (3. çözüm kapsamı dışı olduğu için `memory/GraphMemory.java`'ya **dokunma**.)
- [ ] **`[D2]` Kullanılmayan arayüz/sınıfları sil:**
  - `src/game/gamerepo/IDetermineEdgeValue.java` — yalnızca `BuildGame`'de yorum içinde.
  - `src/game/play/ChangeAbleStartLocation.java` — hiç implemente edilmemiş.
  - `src/game/location/direction/DirectionCompassValues.java` — hiçbir yerde
    kullanılmıyor (aynı bilgi `DirectionCompass`/`KeyboardCompass`'ta zaten var).
- [ ] **`[D3]` `MathFunctionForSecondSolution` içindeki ölü bloklar** (~18 satır):
    `buildNavigation()` altındaki yorumlu eski gövde (`:92-104`),
    `calculateDeadlyPoint()` içindeki yorumlu eski hesap (`:196-201`),
    `decideDeadlyPointCalculation` yorumlu metodu (`:204-209`),
    `addNavigationToRoadMemoryList` içindeki yorumlu satır (`:214`).
- [ ] **`[D4]` `PlayGame` içindeki ölü/yorum kod** (~15 satır): `startLocationX/Y`
    yorumları (`:24, :45-46`), `playGame()` içindeki yorum blokları (`:59-73`),
    `calculatePlayerTotalWinScore` yorumları (`:112-113`),
    `saveGameResultToScore` yorumlu `System.out`'lar (`:124, :126`).
- [ ] **`[D5]` `Robot.java` içindeki ölü kod**: constructor'daki yorum bloğu
    (`:23-32`), yorumlu `getInput` (`:40-43`), yorumlu `RoadMemory` alanı (`:19`),
    yorumlu `getRoadMemory` (`:110-112`).
- [ ] **`[D6]` Kullanılmayan `import`'lar** — özellikle 22 dosyadaki
    `import errormessage.joptionpanel.ShowPanel;` (kullanımı `[L4]` ile kalkınca).
    IntelliJ: `Code → Optimize Imports` proje geneli.
- [ ] **`[D7]` `Main.java` içindeki büyük yorum blokları**: `openWebpage` yorumlu
    metotları (`:100-120`), `main` içindeki yorumlu `SecondSolution` karşılaştırma
    bloğu (`:47-56`), yorumlu `Player` importları/dönüşleri.
- [ ] **`[D8]` `RobotGameOver` temizliği**:
  - `isRobotFinishedFirstSquare()` (`:32-40`) ölü — sil (sadece `isGameOver`
    içindeki yorumda geçiyor).
  - `isGameOver` içindeki yorumlu satırları (`:23, :27`) sil.
- [ ] **`[D9]` `SwitchDirection.choseDirection()` içindeki yorumlu ikinci
    implementasyon** (`:73-98`) — sil.
- [ ] **`[D10]` `Validation.isInputValidForArray()` içindeki yorumlu
    `switchDirection` satırları** (`:69-70`) ve `//System.out.println("AACACA")`
    (`:60`) — sil.
- [ ] **`[D11]` `Player.java` içindeki yorumlu constructor'lar** (`:40-58`) ve
    `CheckAroundSquare.java` içindeki yorumlu `getNumberOfHowManySquaresAreAvailable`
    + `createLocationToCheck` — sil.
- [ ] **`[D12]` `PrepareGame.switchDirection` alanı** kullanılmıyor (`:11, :29`) — sil.
- [ ] **`[D13]` `MoveForward` constructor'daki `MoveForward t = this;`** (`MoveForward.java:11`)
    ölü satır — sil.
- [ ] **`[D14]` `StringFormat.converNumberToReadableNumbers` içindeki yorumlu
    alternatif algoritma** (`:66-77`) — sil.
- [ ] **`[D15]` `NavigationService` içindeki yorumlu constructor + alanlar**
    (`:4-12`) ve `getCompulsoryLocation` içindeki yorum yığını (`:35-47`) — sil.
- [ ] **`[D16]` `ComparisonOfSolutions` / `CopyModel` karar**: yalnızca `Main`'in
    yorumlu bloğunda kullanılıyor (fiilen ölü). Ya "iki çözümü karşılaştır"
    özelliği tekrar bağlanacak (ayrı iş), ya da bu iki sınıf + `PlayGame`'deki
    `public ComparisonOfSolutions comparisonOfSolutions` alanı silinecek. Şimdilik:
    en azından `PlayGame`'deki kullanılmayan `public` alanı kaldır.

---

## 4. Öncelik 3 — Boolean anti-pattern ve gereksiz `== true` / `== false`

- [ ] **`[B1]` `if (X) return true; return false;` → `return X;`** — proje genelinde
  ~20+ metot. Tespit edilen yerler (tam liste değil):
  - `Validation.java`: `validateSquareNumbers`, `needToCalculateBySum`,
    `calculateValidOrNot`, `isInputValidForArray`, `isStepValueAvailable`
  - `SquareProcess.java`: `isSquareAvailableToMoveOnIt`
  - `CheckSquare.java`: `isSquareFreeFromVisitedArea`, `isSquareFreeFromVisitedDirection`,
    `isAnySquareAvailableInVisitedDirection`
  - `MathFunctionForSecondSolution.java`: `isNavigationInRoadMemoryAvailableForThisStep`,
    `isExitSituationLocated`, `isAvailableWayEqualsToZero`,
    `isNextStepWillBeEqualsToTotalSquareValue`, `isOneWayNumberTooMuchToRunHealtyTheAlgorithm`
  - `MoveForwardSecondSolution.java` / `MoveBackSecondSolution.java`: `isNavigationNull`,
    `isDirectionSame`
  - `Move.java`: `isRequiredToChangeStartLocation`
  - `MoveBackSecondSolution.java`: `isRequiredToChangeStartLocation`
  - `RobotGameOver.java`: `isRobotFinishedAllLocations`, `allDirectionsAreVisitedAtStep1`
  - `PersonGameOver.java`: `isGameOver`
  - `PersonInput.java`: `checkInputForForward`, `checkInputForBack`, `isMoveableDirectionInput`
  - `SafeScannerInput.java`: `isNumberProper`
  - `Navigation.java`: `getCompulsoryLocation` (`if (x != null) return x; return null;` → `return x;`)
  - IntelliJ: *Analyze → Inspect Code → "Simplify boolean expression"* ile toplu.
- [ ] **`[B2]` Gereksiz `== true` / `== false`:**
  - `MoveBack.java:27` — `isLockedCounterOfMovingBackLose() == true` → çıplak çağrı
  - `CheckSquare.java` — `getVisitedDirections()[...][...] == false` → `!...`
  - `CheckSquare.isAnySquareAvailableInVisitedDirection` — `... == true`
  - `PrintArray.java` — `array[i][j] == true`
  - `StringFormat.java` — `array[squareIndex][directionIndex] == true`

---

## 5. Öncelik 4 — İsimlendirme ve yazım hataları

IntelliJ `Shift+F6` (Rename) ile tüm referanslar otomatik güncellenir.

- [ ] **`[N1]` Sınıf adı yazım hataları:**
  - `SelectFirstSqaureToStart` → `SelectFirstSquareToStart`
  - `InpectingForwardLocation` → `InspectingForwardLocation`
  - `EasylyReadNumber` → `EasilyReadNumber`
  - `ThirdtSolution_GoldenSquare` → **3. çözüm, DOKUNMA** (not düş, sonraya)
- [ ] **`[N2]` Metot adı yazım hataları:**
  - `BaseSolution.prepareation()` → `prepare()` / `prepareLocation()`
  - `StringFormat.converNumberToReadableNumbers` → `convertNumberToReadableNumbers`
  - `ConvertVariable.StringToInt` → `stringToInt` (Java metotları küçük harfle başlar)
  - `MovePlayer.changePlayerLocationByExcatlyLocation` / `xChangeLocationByExcatly` → `...Exactly...`
  - `SelectFirstSqaureToStart.fillCordinates` → `fillCoordinates`
  - `RoadMemory.updateExistSituation` → `updateExitSituation`
  - `isOneWayNumberTooMuchToRunHealtyTheAlgorithm` → `...Healthy...`
  - `FileWriteProcess.updateFileWriteOrAppendCondtion` → `...Condition`
  - `ConvertNanoTimeToTime` — `nanoTime` alanı aslında **ms** tutuyor,
    `ms = 1000` sabiti "saniyedeki ms" anlamında; `hour:minute:second:nanoTime`
    çıktısındaki son alan artık-ms. Alan/sabit adları yanıltıcı → `millis`,
    `MILLIS_PER_SECOND`, çıktı alanı `millisRemainder`.
- [ ] **`[N3]` Kullanıcıya görünen / yorum yazım hataları:**
  - `Main.java:94` — `"Unknow choice "` → `"Unknown choice"`
  - `Player.java:48` (yorumlu) — `"Unknow ..."` (blokla birlikte zaten silinecek)
  - `ShowPanel`/`ErrorMessage` mesajlarındaki `"Unknow Option"`, `"occured"`,
    `"Stuation"`, `"Sealation"` — düzelt (bu mesajların çoğu `[L4]` ile zaten kalkacak).
- [ ] **`[N4]` `MoveForwardSecondSolution.isDirectionSame` isim çakışması yok ama
    `SealationOfLocation` → `SealingOfLocation` / `LocationSealer`.**
- [ ] **`[N5]` `updateVisitedDirection` parametresi `boolean sealOrUnseal`** —
    `Signature` enum'u zaten var (`SEAL`/`UNSEAL`). Parametreyi `Signature`
    yapmak niyeti netleştirir (davranış aynı; `signature.isSealed()` çağrılır).
- [ ] **`[N6]` Dil tutarlılığı:** kod tabanında Türkçe + İngilizce yorum karışık.
    Bir dil seç (öneri: İngilizce identifier + kısa İngilizce yorum; algoritma
    açıklamaları `sozde-kod.md`'de Türkçe kalabilir). Bu turda: en azından yeni
    yazılan/temizlenen yorumlar tek dil.

---

## 6. Öncelik 5 — Encapsulation (public mutable alanlar)

- [ ] **`[E1]` `BaseSolution.playerLocation` (public)** → `private` + `getPlayerLocation()`.
  Kullanım: alt sınıflar `prepareation()` sonrası okuyor; getter yeterli.
- [ ] **`[E2]` `Move.game` (public)`, `Move.updateValuesInGameModel` (public)** →
  `protected` + gerekiyorsa getter. Alt sınıflar aynı pakette/çocuk.
- [ ] **`[E3]` `PlayGame.comparisonOfSolutions` (public)** → `[D16]` ile birlikte
  ya kaldır ya `private`.
- [ ] **`[E4]` `BaseControlInput.game` (public)** → `protected`.
- [ ] **`[E5]` `Score.lockedCounterOfMovingBackLose` (public)** → `private`
  (zaten `isLockedCounterOfMovingBackLose()` / `lock...` / `unlock...` metotları var).
- [ ] **`[E6]` `Main.baseSolution` (paket-private alan, static bağlamda kullanılıyor)**
  — `selectPlayer` içinde set edilip `main`'de okunuyor. `selectPlayer`'ın dönüş
  tipini kullanıp alanı kaldır, ya da çözüm adını `Player`'dan al (`[R1]` ile aynı çözüm).
- [ ] **`[E7]` `Compass` alanları paket-private (`int north;` ...)** — getter/setter
  zaten var; alanları `private` yap.

---

## 7. Öncelik 6 — Magic number → isimli sabit

- [ ] **`[M1]` Yön hareket vektörleri `±3` (dik) / `±2` (çapraz)** —
  `North/South/East/West` `getX()/getY()` = `±3`, `NorthEast/...` = `±2`.
  - Bu değerler oyunun **tanımı** (8 yöne ~eşit uzaklıkta "sıçrama"). Yanlış değil
    ama **hiçbir yerde açıklanmıyor**.
  - Çözüm: yön `enum`'una (`[A1]`) geçerken `ORTHOGONAL_STEP = 3`,
    `DIAGONAL_STEP = 2` isimli sabitler + 1-2 satır "neden bu değerler" yorumu.
  - **Bu turda tek başına değiştirme** — `[A1]` ile birlikte gelsin ki dağılmasın.
- [ ] **`[M2]` `Validation.validateSquareNumbers` içindeki `minimum = 4`** →
  `MIN_SQUARE_EDGE = 4` sınıf sabiti + "kenar > 4 olmalı" yorumu.
  Ayrıca `SquareValidationGame` mesajı `"must be bigger than 4"` bu sabite bağlansın.
- [ ] **`[M3]` `MathFunctionForSecondSolution`: `oneWayNumbersValue == 2`,
    `>= 3`** → `SECOND_ONE_WAY_MEANS_EXIT = 2`,
    `MAX_ONE_WAY_BEFORE_ABORT = 3` + kısa gerekçe (bkz. `sozde-kod.md` §6.2).
- [ ] **`[M4]` `WeightOfAvailableWay`: `weightOfDirection.length - i` (yani `8 - n`)**
  → yorum: "ileri açıklığı az olan yön daha yüksek ağırlık alır" (`sozde-kod.md` §2).
- [ ] **`[M5]` `edge * edge` / `Math.pow(edge, 2)` (toplam kare sayısı)** →
  `Model.getTotalSquareCount()` (bkz. `[R2]`). Geçtiği yerler: `MoveBack`,
  `PlayGame.calculatePlayerTotalWinScore`, `Validation.isStepValueAvailable`,
  `MathFunctionForSecondSolution.isNextStepWillBeEqualsToTotalSquareValue`
  (`(edgeValue * edgeValue) - 1`), `Player.clearVisitedDirections`.
- [ ] **`[M6]` `step == 1` (başlangıç karesi kontrolü)** birçok yerde —
  `FIRST_STEP = 1` sabiti veya `player.isAtFirstStep()` yardımcı metodu.
- [ ] **`[M7]` `CreateLocation.getId()` / `SelectFirstSqaureToStart.getId()` = `-1`**
  (sentinel) → `NO_DIRECTION_ID = -1` isimli sabit + yorum.
- [ ] **`[M8]` `SafeScannerInput.isNumberProper`: `0 <= value && value < 10`** →
  `MIN_INPUT = 0`, `MAX_INPUT_EXCLUSIVE = 10` + mesajla tutarlı hale getir
  ("0 ve 10 arası" mesajı ile `< 10` uyumlu, dokümante et).

---

## 8. Öncelik 7 — Sıcak yol (hot path) performans temizlikleri

Kullanıcının açık isteği: **hız**. Bunlar davranışı değiştirmez, sadece döngü başına
ayırma (allocation) maliyetini düşürür.

- [ ] **`[H1]` `new LocationsList()...getListOfLocationsAccordingToPlayerCompass(compass)`
      her çağrıda yeni `ArrayList` + 9 nesne üretiyor.**
  - Sıcak çağrı yerleri: `MathFunctionForSecondSolution.calculateForwardAvailableDirectionsOfCurrentDirection`,
    `InpectingForwardLocation.inspectLocationAndGetAvailableSquareNumbers` (her yön için!),
    `CheckSquare.isAnySquareAvailableInVisitedDirection`,
    `MoveBack.clearAllDirectionBeforeGoBack`, `FirstSolution_Combination.getLocationInput`.
  - Çözüm: Yön listesi `Compass`'a göre **değişmez**. Compass başına bir kez üretilip
    cache'lenmeli (ör. `LocationsList` içinde `static Map<Class<? extends Compass>, List<DirectionLocation>>`
    ya da `Robot`/`Player` üzerinde hazır tutulan bir alan).
  - **Dikkat:** `DirectionLocation` nesneleri `setCompass` ile mutasyona uğruyor;
    paylaşımlı cache'e geçerken bu güvenli mi doğrulanmalı (compass tek tip olduğu
    için sorun yok gibi ama kontrol et). Yön `enum`'u (`[A1]`) bunu tamamen çözer.
- [ ] **`[H2]` `InpectingForwardLocation.inspectLocationAndGetAvailableSquareNumbers`
      `ArrayList<Location>` döndürüyor ama çağıran yalnızca `.size()` kullanıyor.**
  - Yer: `MathFunctionForSecondSolution.java:117-118`.
  - Çözüm: metot doğrudan `int` döndürsün (kodun içinde zaten yorumlu `int` sürümü var).
    Her yön denemesinde bir liste ayırmaktan kurtulur.
- [ ] **`[H3]` `MathFunctionForSecondSolution` her adımda şunları `new`liyor:**
    `new CalculationDeadlyPoint(game)` (satır 197), constructor'da `new LocationsList()`
    **iki kez** (satır 41, 43), `new InpectingForwardLocation()` (satır 111),
    `new WeightOfAvailableWay()` (alan — dizi + döngü her nesnede).
  - Çözüm: `WeightOfAvailableWay` ve `weightOfDirection` dizisi `static final`
    (değişmez). `CalculationDeadlyPoint` sadece `game` tutuyor → tek örnek yeniden
    kullanılabilir ya da statik metoda dönüşebilir. `LocationsList` çağrısı `[H1]`.
- [ ] **`[H4]` `Person.getCompass()` her çağrıda `new KeyboardCompass()`** →
  `Robot`'ta yapıldığı gibi alana al (`[P3]`). (Person sıcak yol değil ama tutarlılık.)
- [ ] **`[H5]` `PlayGame.getEasyReadyNumber` / `Move` / `MoveThird` her çağrıda
      `new EasylyReadNumber()` + içinde `new StringFormat()`.**
  - Bunlar döngü **dışı** (oyun sonu raporu) — düşük öncelik. Yine de birer alana
    almak temiz. `EasilyReadNumber`'ı stateless yapıp `static` kullanmak en temizi.
- [ ] **`[H6]` `GameModelProcess.calculateIndexOfGivenStepInGameSquareArrays` /
      `deleteMaxStep` tüm tahtayı tarıyor (O(edge²)).**
  - `deleteMaxStep` her geri adımda çağrılıyor (`MoveBack.removeMaxStepBeforeGoingLastStep`).
  - `calculateIndexOfGivenStepInGameSquareArrays` `break` bile kullanmıyor (bulduktan
    sonra taramaya devam ediyor).
  - Çözüm (bu turda minimum): bulunca `return` et. Tam çözüm: adım→koordinat
    eşlemesini bir dizide tut (ayrı iş, davranış analizi ister — **not düş**).

---

## 9. Öncelik 8 — Hata yönetimi

- [ ] **`[X1]` `ErrorMessage.throwError` genel `Exception` fırlatıyor**
  (`ErrorMessage.java:18`). Çağıran (`SquareValidationGame`) `catch (Exception)`'a
  mecbur. Kendi tipini tanımla: `class InvalidGameConfigException extends Exception`.
- [ ] **`[X2]` `BuildGame.buildGame` `catch (Exception e)` + `System.out` + özyineleme**
  (`BuildGame.java:20-27`). Sadece `InvalidGameConfigException` yakala; mesajı
  `Trace`/`System.out` yerine düzgün bir kullanıcı mesajı olarak ver; `this.edgeValue = edgeValue;`
  **iki kez** yazılmış (`:19` ve `:21`) — birini sil.
- [ ] **`[X3]` `Validation.isInputValidForArray` `catch (Exception ex)`** (`:75`) —
  aslında sadece `NullPointerException` bekleniyor (compass'tan `null` yön).
  Spesifik yakala; `ErrorMessage.appearClassicError` (Toolkit.beep + print) yerine
  `Trace`. Sıcak yolda bu blok tetiklenirse `beep()` çok yavaş.
- [ ] **`[X4]` `SealationOfLocation.updateLocationCondition` `catch (ArrayIndexOutOfBoundsException)`
      + `ShowPanel` + `printStackTrace`** (`:18-24`). Bu yakalama bir **bug maskeliyor**
  olabilir (geçersiz koordinata yazım). En azından `Trace.ENABLED` ile logla ve
  `[R6]`/`[R5]` ile kök nedeni ele al. `printStackTrace` → `Trace`.
- [ ] **`[X5]` `NavigationService.getCompulsoryLocation` `throw new NullPointerException`
      ile akış kontrolü** (`:41`) — `[R7]` ile birlikte kaldır (`null` döndür + kontrol).
- [ ] **`[X6]` `printStackTrace()` çağrıları (6 adet)** → hepsi `Trace` /
  `ErrorMessage.appearWarnings` üzerinden. `FileWriteProcess.openFile`'daki
  `e.printStackTrace()` → en azından `ErrorMessage`.
- [ ] **`[X7]` `ConvertVariable.StringToInt` hata durumunda `-1` sentinel döndürüyor**
  (`:14-20`). Çağıranlar `-1`'i geçerli değerden ayırmıyor. Bu turda: en azından
  `Trace` ile logla; ileride `OptionalInt`.
- [ ] **`[X8]` `Robot.updateVisitedDirection` içindeki `assert`** (`:73`) —
  `assert` JVM'de varsayılan kapalı; ya gerçek bir kontrol (`if (...) throw`) ya da
  `Trace`. Türkçe büyük harf mesaj da düzeltilsin.

---

## 10. Öncelik 9 — Mimari (daha büyük, en sona)

- [ ] **`[A1]` 8 yön sınıfı + `DirectionCompassValues` → tek `enum Direction`.**
  - Bugün: `North, NorthEast, East, SouthEast, South, SouthWest, West, NorthWest,
    LastLocation` = 9 dosya, her biri `getId()` (compass'tan) + hareket vektörü
    (`getX/getY` = `±2/±3`) taşıyor. `SwitchDirection` bunları 9'lu `if` zinciriyle
    seçiyor. `LocationsList` 9'unu elle ekliyor.
  - Hedef: `enum Direction { NORTH(dx,dy), NORTH_EAST(dx,dy), ..., BACK; }` +
    compass eşlemesi `Compass` içinde `EnumMap` ya da `Direction.ordinal()`.
  - Kazanç: `SwitchDirection` `switch`/lookup'a iner, `LocationsList` `values()`
    olur, `[H1]` (allocation) ve `[M1]` (magic number) kendiliğinden çözülür,
    derleyici tüm yönlerin ele alındığını kontrol eder.
  - **En riskli refactor** — `sozde-kod.md` + testler (`[A5]`) hazır olduktan sonra.
- [ ] **`[A2]` `(Robot) game.getPlayer()` cast'lerini kaldır.** Geçtiği yerler:
  `MathFunctionForSecondSolution:39, :215`, `CalculationDeadlyPoint:13`,
  `NavigationService.getRobot`, `MoveForwardSecondSolution:17`, `MoveBackSecondSolution:19`,
  `PlayGame:139`.
  - Bu tip cast'ler 2. çözümün yalnızca `Robot` ile çalıştığını varsayıyor
    (aslında doğru — `Person` 2. çözümü kullanmıyor). Yine de: `RobotMemory`/
    `RoadMemory` erişimi bir arayüz arkasına alınabilir (`HasRobotMemory`) ya da
    2. çözüm sınıfları doğrudan `Robot` referansı taşıyabilir (constructor'da bir kez cast).
- [ ] **`[A3]` `MathFunctionForSecondSolution` (219 satır) SRP bölünmesi.**
  - Sorumluluklar: (1) RoadMemory'den navigation okuma, (2) 8 yön tarama + ağırlık,
    (3) tek-yol/exit tespiti, (4) ölü nokta hesabı, (5) navigation inşa + yazma.
  - `sozde-kod.md` §10'da önerildiği gibi: `DeadlyPointCalculator`,
    `OneWayExitTracker`, `ForwardWayScanner` gibi küçük iş birimlerine ayır;
    `MathFunctionForSecondSolution` bunları orkestre eden ince sınıf olsun.
  - **En zaman alıcı madde; en sona.**
- [ ] **`[A4]` `SelectFirstSqaureToStart extends DirectionLocation`** — kalıtım
  istismarı (bir "yön" değil). `Location`'ı kompozisyonla kullansın, ya da sadece
  `int x, y` tutsun.
- [ ] **`[A5]` İlk birim testleri.** Yan etkisiz saf sınıflardan başla:
  `Validation` (`validateSquareNumbers`, `calculateValidOrNot`, `isStepValueAvailable`),
  `CalculationDeadlyPoint.calculateDeadlyPoint` (tablo `sozde-kod.md` §7'de hazır),
  `WeightOfAvailableWay`, `StringFormat.converNumberToReadableNumbers`,
  `ConvertNanoTimeToTime`, `BaseSolution.getSolutionFileName`.
  - Bunlar `[A1]`/`[A3]` refactor'ünü güvene alır.
- [ ] **`[A6]` Build sistemi (Maven/Gradle).** Şu an ne `pom.xml` ne `build.gradle`
  var (NetBeans `build.xml` + `nbproject/`). JUnit eklemek, "tek komutla test"
  ve CI için gerekli. `javac.source/target = 1.8` korunabilir (ama `enum`/`switch`
  zaten 1.8'de var, sorun yok).
- [ ] **`[A7]` `Game(Model, Player)` kullanılmayan constructor** — `BuildGame`
  parametresiz `new Game()` kullanıyor. Kullanılmayanı sil ya da tek constructor'a indir.
- [ ] **`[A8]` `TimeKeeper` paketi `game.gamerepo.player.robot` ama `Person` da
  kullanıyor** (`Score.updatePlayedTime` → `player.getTimeKeeper()`). `game.time`
  gibi nötr bir pakete taşı.
- [ ] **`[A9]` `Player.clearVisitedDirections()` `game.getPlayer().getCompass()`
  çağırıyor** — `this` yerine `game`'den kendini alıyor (dolambaçlı, `setGame`
  sırasında `game.setPlayer(this)` yeni yapıldığı için çalışıyor). `getCompass()`
  doğrudan çağrılsın.

---

## 11. Öncelik 10 — Proje/dosya tutarlılığı

- [ ] **`[C1]` Satır sonu tutarsızlığı.** Bazı dosyalar CRLF, bazıları LF.
  `.gitattributes` ekle (`*.java text eol=lf`) + bir kerelik normalizasyon.
- [ ] **`[C2]` `.editorconfig` ekle** (indent, charset=utf-8, eol=lf,
  trim_trailing_whitespace). IDE ayarından bağımsız tutarlılık.
- [ ] **`[C3]` `.gitignore` gözden geçir.** İçinde `portfolio-parent/`,
  `frontend/.scratch/` gibi **başka bir projeye ait** kurallar var (yanlış kopya).
  Bu projeye uymayan satırları temizle; `build/`, `dist/`, `*.class`, `*_Completed.txt`,
  `*_EverySingleSquareTotalValue.txt` kalsın.
- [ ] **`[C4]` `txt-backup/` ve kök dizindeki `.txt`/`.png` "taktik" dosyaları**
  bir `docs/` klasörüne toplansın (kök dizin karışık görünüyor).
- [ ] **`[C5]` `src/META-INF/MANIFEST.MF` + kökte `manifest.mf` + `nbproject`** —
  build sistemi kararıyla (`[A6]`) birlikte sadeleştir.
- [ ] **`[C6]` Paket adı `Main` büyük harf** (`src/Main/Main.java`) — Java
  konvansiyonu küçük harf (`app` / `main`). Rename.

---

## 12. Önerilen uygulama sırası (özet)

Risk düşük + etki yüksek → risk yüksek:

1. **Bölüm 1 (`[R1]`–`[R7]`)** — bug'lar; önce bunlar (özellikle `[R1]` ClassCastException).
2. **Bölüm 2 (`[L1]`–`[L7]`)** — `Trace` altyapısı + `System.out`/`ShowPanel` taşıma.
   Bundan sonra test modunda loglama açıp adımların doğruluğu izlenebilir.
3. **Bölüm 3 (`[D1]`–`[D16]`)** — ölü kod temizliği (risk ~0).
4. **Bölüm 4 (`[B1]`–`[B2]`)** — boolean sadeleştirme (IntelliJ inspection, mekanik).
5. **Bölüm 5 (`[N1]`–`[N6]`)** — rename (IDE güvenli).
6. **Bölüm 6 (`[E1]`–`[E7]`)** — encapsulation.
7. **Bölüm 7 (`[M2]`–`[M8]`)** — magic number sabitleri (`[M1]` hariç, o `[A1]` ile).
8. **Bölüm 8 (`[H1]`–`[H6]`)** — hot-path allocation temizliği (hız kazancı).
9. **Bölüm 9 (`[X1]`–`[X8]`)** — hata yönetimi.
10. **`[A5]` testler → `[A6]` build → `[A1]`, `[A2]`, `[A4]`, `[A7]`–`[A9]` → `[A3]`** — mimari.
11. **Bölüm 11 (`[C1]`–`[C6]`)** — istediğin zaman.

> Her adımdan sonra: 5x5 ve 6x6 için 2. çözümü çalıştır, `total Solved` değerinin
> **12_400 (5x5)** ve bilinen 6x6 değeriyle aynı kaldığını doğrula. Değer değişirse
> o adım mantığı bozmuş demektir → geri al.
