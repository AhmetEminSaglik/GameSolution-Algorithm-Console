# TODO — Çözümleri PostgreSQL'e Batch Kaydetme + Dikdörtgen Grid

> Otonom çalışma listesi. Her madde bitince `[ ]` → `[x]`. Her fazın sonunda
> commit + bu dosyayı güncelle. **Regresyon testleri (`mvn test`) her fazda
> yeşil kalmalı** ve DB olmadan çalışmalı (DB opsiyonel/flag'li).

---

## 0. Tasarım kararları (kullanıcı "sen yorumla" dedi)

### 0.1 Path encoding — "byte[]'a mı 4'lü byte'a mı çevirmiştik?"
Portfolyo projendeki `(x<<4)|(y&0x0F)` = **hücre başına 1 byte, koordinat başına 4 bit → 16x16'da tavan yapar.** 20x20'de patlar.

**Karar: yön-kodlaması (direction encoding), adım başına 3 bit, bit-packed `BYTEA`.**
- Çözüm = başlangıç hücresi + (N−1) sıçrama yönü. 8 olası yön → 3 bit.
- Yeniden kurulum: başlangıçtan itibaren yönleri oynat.
- 10x10: 99 adım × 3 bit ≈ **38 byte**. 100x100: ≈ **3.7 KB**. Her grid boyutunda çalışır.
- Decode için `start_row/start_col/row_size/col_size` (SMALLINT) sütunları yeter.
- Codec ayrı sınıf (`PathCodec`), değiştirilebilir. Basit alternatif (mutlak hücre
  indeksi, ≤256 hücre için 1 byte / değilse 2 byte) yorum olarak bırakılacak.

### 0.2 Ölçek gerçeği — "her çözümü saklamak"
5x5=12.400 · 6x6≈8M · 7x7 (tek başlangıç)=49M → tüm başlangıçlar ≈ 2 milyar ·
10x10 = muhtemelen **trilyonlar**. Trilyon satır saklanamaz.

**Karar:**
- **Şimdi:** her çözümü sakla (≤6x6 rahat, ~7x7 zorlu; batch altyapısı asıl değer).
  Opt-in flag arkasında.
- **Normalize et:** run-seviyesi metrikler (`roundCounter`, `elapsedMs`,
  `totalBackSteps` …) her satırda tekrar edilmez → ayrı **`solver_run`** tablosu.
- **Partition:** `path_explorer_solution` → `(row_size, col_size)` LIST partition.
  Her grid boyutu kendi partition'ı; kolay `DROP`.
- **`opening` sütunu** (indexli) = ilk K hücrenin paketlenmiş hali → "şu açılıştan
  kaç çözüm var" sorgusu ucuz `GROUP BY`, BYTEA taramadan.
- **~7x7 üstü:** yalnızca-aggregate moda geç — `(rows, cols, start, opening) → count`
  tablosu. Şema buna **eklemeli** geçişe uygun tasarlandı. Şimdi yapılmıyor, not düşüldü.

### 0.3 Framework
Bu proje düz Java (Spring yok). **Düz JDBC + HikariCP**, elle yazılmış batch
repository. Hibernate'ten hızlı bulk insert, minimum bağımlılık. (Portfolyo Spring
Boot kodu ayrı proje; sadece isim/fikir referansı.)

### 0.4 Anahtarlar
- `id BIGINT GENERATED ALWAYS AS IDENTITY` — DB atar, geri okunmaz, batch-dostu.
- `public_id UUID` (Java `UUID.randomUUID()`) — **evet ekle.** 16 byte, ucuz;
  ileride başka tabloyla eşleştirmenin güvenli yolu. Unique index.
- `solution_index BIGINT` — run içi kaçıncı bulundu. Global unique değil, sadece veri.
- Sequence generator: düz JDBC + IDENTITY ile gerek yok. JPA'ya geçilirse pooled
  sequence (`allocationSize=1000`) batch-dostu seçim — not düşüldü.
- **Thread:** her solver thread → kendi `solver_run` + kendi buffer + kendi
  `solution_index` sayacı. DB IDENTITY eşzamanlı insert'i halleder. Koordinasyon yok.

### 0.5 createdAt hassasiyeti
Postgres `timestamptz` = **mikrosaniye** (6 hane) tavanı — **nanosaniye
saklanamaz.** `created_at = Instant.now()` constructor'da (yazarken µs'e kırpılır).
Gerçek sıralama = `solution_index`. Nano şart olursa `created_epoch_nanos BIGINT`
eklenir — ama satır oluşturma zamanı için µs zaten fazlasıyla yeterli.

### 0.6 Batch
JDBC `addBatch()`/`executeBatch()`, 1000'de bir flush, **kalan <1000 için oyun
bitince son flush**, **Ctrl+C'de shutdown hook** → flush + run'ı ABORTED işaretle.
JDBC URL `reWriteBatchedInserts=true`. `autocommit=false`, batch başına commit.

### 0.7 Deploy
`docker-compose.yml` (Postgres 16) + `docker/initdb/01_schema.sql` (ilk boot'ta
otomatik). `docker compose up -d` → hazır DB. Compose (bare `docker run` değil),
lokal ve remote'ta aynı.

---

## FAZ 1 — Dikdörtgen grid desteği  ✅ (2026-08-31)

- [x] `Model`: `getRowCount()`, `getColCount()` eklendi; `getTotalSquareCount()` → `rows * cols`.
- [x] `Validation.isInputValidForArray`: X sınırı = `getRowCount()`, Y sınırı = `getColCount()`.
- [x] `RobotGameOver`: `squareEdge` → `rowCount`/`colCount`; bitiş `(rows-1, cols-1)`.
- [x] `MathFunctionForSecondSolution`: `edgeValue` alanı → `totalSquareCount`;
      `(edgeValue*edgeValue)-1` → `totalSquareCount-1`.
- [x] `BuildGame`: `rowCount`/`colCount`; `BuildGame(int rows, int cols)` + `BuildGame(int edge)`
      kare kısayolu; `buildVisitedArea` → `[rows][cols]`; `clearVisitedAreas` iç döngü `[i].length`;
      `determineGridSize()` "5" / "5 6" / "5x6" kabul ediyor.
- [x] `Move`: `squareEdge` → `rowCount`/`colCount`; `changeStartLocationSpecialMovement`
      X sınırı rows, **Y sınırı cols (idi rows — dikdörtgende BUG'du)**; format `rowCount-1`.
- [x] `ResetAllDataForGameAndPlayer`: rows/cols; `new BuildGame(rows, cols)`.
- [x] `SelectFirstSqaureToStart` / `GameModelProcess`: zaten `[0].length` / `[i].length`
      kullanıyordu — dokunulmadı (dikdörtgen-güvenli).
- [x] `Main`: dimension çıktısı `getRowCount()`-`getColCount()`; no-arg `BuildGame()`
      artık satır/sütun soruyor.
- [x] `CopyModel` (ölü kod) — dokunulmadı.
- [x] 3. çözüm dosyaları — dokunulmadı (kapsam dışı, kare için bozulmadı).
- [x] `IndependentSolutionCountTest`: `bruteForceCount(rows, cols)` genelleştirildi;
      **5x6 testi eklendi (`@Tag("slow")`, ~2 dk).**
- [x] **DOĞRULAMA:** `mvn test` 16/16 yeşil (5x5 = 12_400 birebir aynı).
      `mvn test -Dgroups=slow`: **5x6 brute-force = 113_456 = 1. algo = 2. algo** →
      dikdörtgen destek bağımsız yöntemle doğrulandı.
- [x] Commit.

## FAZ 2 — Path codec (saf, DB yok)  ✅ (2026-08-31)

- [x] `src/persistence/PathCodec.java`: `encode(int[][] cells)` (3 bit/adım, bit-packed,
      MSB-first) + `decode(byte[], startX, startY, pathLength)`. 8 yön vektörü `DIRS`'te.
      Geçersiz hareket → `IllegalArgumentException`.
- [x] `src/persistence/GridPath.java` (record): rowCount, colCount, startX, startY,
      `int[][] cells`; `encode()`, `decode(...)`, `cellIndexAtStep(step)` = x*cols+y.
- [x] `test/persistence/PathCodecTest.java` (4 test): round-trip (5x5/5x6/10x10/12x9,
      her boyut 200 rastgele yol); byte uzunluğu (1 hareket→1B, 3 hareket→2B, tam 5x5→9B);
      geçersiz hareket throw; GridPath encode/decode.
- [x] `mvn test` yeşil (20 test).
- [x] Commit.

## FAZ 3 — Çözüm toplama (solver içinde, DB yok)  ✅ (2026-08-31)

- [x] `src/persistence/SolutionSink.java` — `isEnabled()` (NoOp:false → path çıkarma
      maliyeti bile yok), `beginRun/accept/endRun/close`; iç record'lar `RunInfo`,
      `FoundSolution`, `RunResult`.
- [x] `src/persistence/NoOpSolutionSink.java` — hepsi boş, `isEnabled()=false`.
- [x] `PlayGame`: `PlayGame(Game, SolutionSink)` constructor (eski `PlayGame(Game)`
      → NoOp'a delege). `playGame()` başında `beginRun`, sonunda `endRun`;
      `calculatePlayerTotalWinScore` içinde çözüm bulununca `solutionIndex++` +
      (recordSolutions ise) `extractCurrentPath()` → `sink.accept(...)`.
- [x] `extractCurrentPath()`: `gameSquares[x][y] = k` → `cells[k-1] = {x,y}` → `GridPath`.
- [x] `test/persistence/SolutionSinkHookTest.java`: 5x5 2. çözüm gerçek koşusunda
      **12_400 çözüm yakalanıyor**, her yol 25 hücre + geçerli sıçramalar + doğru
      başlangıç + PathCodec round-trip. (~1.3 sn)
- [x] `mvn test` yeşil (21 test; NoOp default → regresyon sayıları değişmedi).
- [x] Commit.

## FAZ 4 — Docker Compose + Postgres + şema  ✅ (2026-08-31)

- [x] `docker-compose.yml`: `postgres:16`, `pathexplorer-db`, 5432, named volume,
      healthcheck, initdb mount.
- [x] `docker/initdb/01_schema.sql`:
  - `solver_run` (id identity, public_id uuid unique, row/col_size, algorithm,
    total_solved, round_counter, total/dummy_back_steps, elapsed_ms, status
    RUNNING/COMPLETED/ABORTED, started_at, finished_at).
  - `path_explorer_solution` **PARTITION BY LIST (grid_size)** — grid_size =
    row*1000+col (uygulama doldurur; Postgres generated column'u partition key
    kabul etmiyor). id identity, public_id uuid, solver_run_id fk, solution_index,
    row/col_size, start_x/y, path_len, path bytea, open1/2/3 smallint,
    created_at timestamptz. PK (id, grid_size), UNIQUE (public_id, grid_size).
  - Partition: 5x5, 5x6, 6x6, 7x7, 10x10 + DEFAULT.
  - Index: `(solver_run_id)`, `(grid_size, open1, open2, open3)`.
  - Yalniz-aggregate gelecek tablosu SQL yorumunda taslak olarak var.
- [x] `docker/README.md` — komutlar, bağlantı bilgisi, örnek sorgular.
- [x] **DOĞRULAMA:** `docker compose up -d` → şema hatasız kuruldu, 8 tablo
      (`solver_run` + `path_explorer_solution` + 6 partition) `psql \dt` ile görüldü.
- [x] Commit.

## FAZ 5 — JDBC persistence katmanı  ✅ (2026-08-31)

- [x] `pom.xml`: `org.postgresql:postgresql 42.7.4`, `com.zaxxer:HikariCP 5.1.0`,
      `slf4j-nop 2.0.13`. `test.excludedGroups` property (`slow,db`) — db testleri
      `-Dtest.excludedGroups=slow -Dgroups=db` ile.
- [x] `src/persistence/DbConfig.java` — env > `db.properties` (classpath ya da CWD) >
      varsayilan. `isDbEnabled()` = `PATHEXPLORER_DB_ENABLED` bayragi.
- [x] `db.properties` (kök) — lokal compose değerleri (`localhost:5442`, pathexplorer/pathexplorer).
      **Port 5442** — 5432 lokalde native PostgreSQL ile çakışıyordu.
- [x] `src/persistence/JdbcSolutionSink.java`:
  - `beginRun` → `solver_run` insert (RETURNING id) + ayrı uzun-ömürlü batch bağlantısı
    (autocommit kapalı).
  - `accept` → buffer; `size >= batchSize` → `flush()`.
  - `flush()` → tek `PreparedStatement` + `addBatch()`/`executeBatch()` + `commit()`;
    hata → rollback.
  - `endRun` → son `flush()` + `solver_run` COMPLETED update (sayaçlar, finished_at).
  - `close()` → kalan buffer flush + kaynak kapat + shutdown hook kaldır.
  - Shutdown hook → normal bitmemişse flush + `solver_run` ABORTED.
  - `open1/2/3` = ilk 3 adımın hücre indeksi (`x*col + y`); adım yoksa NULL.
  - `grid_size` = `row*1000 + col` (uygulama doldurur).
- [x] `test/persistence/JdbcSolutionSinkDbTest.java` (`@Tag("db")`, DB yoksa `assumeTrue`
      ile atlanır): 5x5 2. çözüm → sink → **12_400 satır DB'ye yazıldı**, `solver_run`
      COMPLETED + total_solved 12_400 + round_counter 1_023_656, bir satır decode edilip
      geçerli 25-hücre yol, `GROUP BY open1` çalışıyor, test verisi silindi. (~2 sn)
- [x] `mvn test` yeşil (21 fast). `mvn test -Dtest.excludedGroups=slow -Dgroups=db` yeşil (1).
- [x] Commit.

## FAZ 6 — Bağlama (Main)

- [ ] `Main`: DB kaydı flag/menü/env (`PATHEXPLORER_DB=1` veya menüde "3) DB'ye kaydet").
      Açıksa `JdbcSolutionSink`, kapalıysa `NoOpSolutionSink`.
- [ ] Algoritma seçimi + start hücresi bilgisi `RunInfo`'ya.
- [ ] Elle deneme: `docker compose up -d` → 5x5 DB'ye kaydet → `psql` ile satır say.
- [ ] Commit: "Faz 6: Main -> opsiyonel DB kaydi".

## FAZ 7 — "Açılıştan kaç çözüm" analitiği

- [ ] `src/persistence/OpeningStatsQuery.java`: `SELECT opening, COUNT(*) ... GROUP BY`
      + ilk 2-3 adımı okunur formata çeviren yardımcı.
- [ ] `test` veya küçük bir `main` ile 5x5'te örnek çıktı.
- [ ] Commit: "Faz 7: acilis istatistigi sorgusu".

## FAZ 8 — Testler + dokümantasyon + rapor

- [ ] `PathCodecTest` genişlet (kenar durumlar).
- [ ] (Opsiyonel, docker gerekiyorsa `@Tag("db")`) `JdbcSolutionSinkIT` — çalışan
      Postgres'e 5x5 yaz, oku, doğrula.
- [ ] `proje-degerlendirme.md` / `gereken-duzenlemeler-2.md` puanları güncelle
      (Test + Mimari + "persistence" artışı).
- [ ] Bu dosyanın en altına **DURUM / DEVAM RAPORU** yaz.
- [ ] Commit: "Faz 8: testler + dokuman + rapor".

---

## DURUM / DEVAM RAPORU

**Son güncelleme:** 2026-08-31, Faz 5 bitti.

**Tamamlanan:** Faz 0-3. Faz 1: 5x6=113_456 dogrulandi. Faz 2: PathCodec 3bit/adim. Faz 3: SolutionSink kancasi (5x5 12_400 cozum yakalandi).

**Sıradaki:** Faz 6 — Main entegrasyonu (opsiyonel DB kaydi).

**Yeni session için notlar:**
- Bu proje düz Java + Maven (Spring YOK). `mvn test` 16 test yeşil olmalı.
- Regresyon ağı: `test/GameRegressionTest.java` (5x5 golden), `IndependentSolutionCountTest`
  (bağımsız brute-force oracle). Her kod değişikliğinden sonra `mvn test`.
- `Trace.ENABLED` = false olmalı (true olursa testler milyonlarca satır basar).
- DB işleri OPT-IN: DB olmadan `mvn test` ve normal çalıştırma bozulmamalı.
- Docker mevcut (27.5.1, compose v2.32).
- Branch: `refactor-yorum-satirlari`.
