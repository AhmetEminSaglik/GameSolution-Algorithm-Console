# Checkpoint Kıyaslama Rehberi (solving_checkpoint vs. DB'ye Yazılamayan Veri)

Bu dosya, Algoritma 2 (`SecondSolution_CalculateForwardAvailableWays`) checkpoint
mekanizmasının **doğru çalışıp çalışmadığını** ("sapıtıyor mu?") doğrulamak için
yapılan incelemenin sonucunu ve tekrar üretilebilir yöntemini kaydeder. Session
kopsa da, başka bir session'dan devam edilebilsin diye buradadır — CHECKPOINT.md
ve PERSISTENCE.md'nin üstüne, spesifik olarak "kıyaslama" konusuna odaklanır.

## 1. Neden bu kıyaslama gerekiyor

`solving_checkpoint` satırları **TAM STATE** bazında tekil (bkz.
`docker/initdb/05_solving_checkpoint_full_state_unique.sql`). Yani bir satırın
DB'de olması, o state'in doğru üretildiğinin kanıtı değil — sadece "bu state
DB'ye YAZILABİLDİ" demek. Eğer DB yazımı (constraint hatası, bağlantı kopması,
vs.) sessizce başarısız olursa, checkpoint listesinde bir **boşluk** oluşur ve
bu boşluk, kullanıcı elle fark etmedikçe sonsuza kadar orada kalır (geçmişe
dönük yeniden üretilemez — bkz. §4).

Bu yüzden kıyaslama iki ayrı soruyu cevaplar:

1. **Algoritma deterministik mi / doğru mu çalışıyor?** — Aynı checkpoint'ten
   iki farklı oturumda devam edildiğinde AYNI state üretiliyor mu?
   (`total_solved`, `round_counter`, `total_back_steps`, `dummy_back_steps`
   birebir eşleşmeli.)
2. **DB'ye yazılamayan veri var mı, varsa neden?** — Bir checkpoint DB'ye
   yazılamazsa, o satırın TAM içeriği ekrana basılıyor mu ki elle/manuel
   olarak DB'deki en yakın satırla kıyaslanabilsin?

## 2. Kıyaslanacak alanlar (tam liste)

`solving_checkpoint` tablosunun TAM STATE unique constraint'indeki tüm
sütunlar + iki "metrik ama constraint dışı" sütun. Kaynak: `docker/initdb/
04_solving_checkpoint.sql`, `src/persistence/checkpoint/Algo2Snapshot.java`,
`src/persistence/checkpoint/Algo2CheckpointWriter.java`.

| Alan | DB sütunu | Constraint'te mi? | Java kaynağı |
|---|---|---|---|
| Solution index | `solution_index` | evet | `Algo2CheckpointWriter.Pending.solutionIndex()` |
| Grid map id | `grid_map_id` | evet | `Algo2CheckpointWriter.gridMapId` (row/col → `grid_map` tablosundan çözülür) |
| Algorithm id | `algorithm_id` | evet | `Algo2CheckpointWriter.algorithmId` |
| Interval size | `interval_size` | evet | `Algo2CheckpointWriter.interval` |
| Step | `step` | evet | `Algo2Snapshot.step()` |
| Path length | `path_len` | evet | `Algo2Snapshot.step()` (aynı değer, iki sütuna yazılıyor) |
| Dir count | `dir_count` | evet | `Algo2Snapshot.dirCount()` |
| Path | `path` (BYTEA) | evet | `Algo2Snapshot.path()` — `path[k]` = (k+1). adımın hücre indeksi (`x*col+y`) |
| Visited dirs | `visited_dirs` (BYTEA) | evet | `Algo2Snapshot.visitedDirs()` — bitset, bit = `step*dirCount+dir` |
| Exit situation | `exit_situation` | evet | `Algo2Snapshot.exitSituation()` |
| One way list | `one_way_list` (BYTEA) | evet | `Algo2Snapshot.oneWayList()` — `count:int` + her nav için `step,oneWayValue,compulsoryDirId,exitLocatedHere` |
| Round counter | `round_counter` (+`_overlong`) | evet | `Algo2Snapshot.roundCounter()` |
| **Total solved** | `total_solved` (+`_overlong`) | **HAYIR** | `Algo2Snapshot.totalSolved()` — şüpheli denilen alan: bilerek constraint dışı, çünkü `solution_index` ile bire-bir orantılı, ayırt edicilik katmıyor. Yine de kolon olarak duruyor (gözle kontrol için) — bu yüzden kıyaslamaya dahil edildi. |
| Total back steps | `total_back_steps` | evet | `Algo2Snapshot.totalBackStep()` |
| Dummy back steps | `dummy_back_steps` | evet | `Algo2Snapshot.dummyBackMove()` |
| Locked back lose | `locked_back_lose` | evet | `Algo2Snapshot.lockedBackLose()` |

`total_solved` constraint dışı olduğu için, teorik olarak "aynı state ama
farklı total_solved" tekilliğe takılmadan ayrı satır olarak eklenebilir —
pratikte `solution_index` ile birebir orantılı olduğundan bu hiç gözlenmedi.

## 3. Bu incelemede bulunanlar (2026-09-21 itibarıyla)

### 6x6 (algorithm_id=2)

- DB'deki gerçek toplam çözüm sayısı **8.250.272**'dir (kullanıcının aklından
  söylediği "8.262.672" bir yazım hatası — doğru rakam DB'de son checkpoint
  satırının `total_solved`'ında VE `rapor/RunStatistic/run-statistic-6-6.txt`
  dosyasındaki "Bastan Calistir" koşularının üçünde de (NONE/CHECKPOINT/FLAT
  modları) birebir aynı: `Total Number Solved: 8_250_272`).
- 6x6 için `path_len` HER ZAMAN 36'dır (6×6 = tam ızgara kapsaması). Yani
  `path_len=49` sorgusu 6x6'yı DEĞİL, **7x7**'yi getirir (49 = 7×7). Bunu
  doğrulamak için: `SELECT DISTINCT path_len FROM solving_checkpoint c JOIN
  grid_map g ON g.id=c.grid_map_id WHERE g.row_size=6 AND g.col_size=6;` → tek
  satır, `36`.
- **Deterministiklik kanıtı:** `run-statistic-6-6.txt` içinde "Checkpoint
  Araligindan Devam" ile yapılmış İKİ AYRI koşu var, ikisi de `Total Number
  Solved: 600_000`, `Total Back Step: 53_130_238`, `Total Step: 106_260_513`,
  `Total Dummy Back Step: 10_088_219` — rakamlar birebir aynı. Bu değerler
  DB'deki `solution_index=600000` satırıyla da birebir eşleşiyor
  (`round_counter=106260513`, `total_back_steps=53130238`,
  `dummy_back_steps=10088219`, `total_solved=600000`). **Sonuç: checkpoint'ten
  devam edip aynı aralığı tekrar oynatmak, orijinal koşunun ürettiği state'i
  BİREBİR yeniden üretiyor — algoritma sapıtmıyor, deterministik çalışıyor.**
- Tam koşunun (8.250.272 çözüm) SON checkpoint satırındaki toplamlar
  (`total_back_steps=735.390.001`, `round_counter=1.470.780.072`,
  `dummy_back_steps=138.332.916`), koşu TAMAMEN bittiğindeki rapor
  toplamlarından (`735.409.099` / `1.470.818.233` / `138.336.510`) biraz
  düşük — bu bir HATA DEĞİL, CHECKPOINT.md'de belgelenmiş beklenen davranış:
  "bir checkpoint anlık görüntüdür; koşu sonu toplamları her zaman son
  checkpoint'ten büyüktür" (son çözümden sonra arama tükenene kadar geri
  sarma devam eder).

### 7x7 (algorithm_id=2)

- `solution_index=508.000.000` checkpoint satırı, `run-statistic-7-7.txt`
  içindeki ikinci "Checkpoint Araligindan Devam" kaydıyla (aynı `round_counter`
  / `total_back_steps` / `dummy_back_steps` / `total_solved`) BİREBİR eşleşiyor
  → büyük ölçekte de restore+replay doğru çalışıyor.
- **Gerçek boşluk bulundu:** `solution_index` 500.000'den 1.000.000'e atlıyor
  (600.000 / 700.000 / 800.000 / 900.000 checkpoint'leri DB'de YOK). Bunun
  kökeni **algoritma hatası değil, bir DB migration/altyapı hatası**:
  - `git log` (commit `762c88e`, 2026-09-18) şunu açıklıyor: TAM STATE unique
    constraint migration'ı (`05_solving_checkpoint_full_state_unique.sql`)
    daha önce commit'lenmişti ama canlı `dev-postgres`'e HİÇ UYGULANMAMIŞTI.
    O yüzden her `ON CONFLICT` INSERT'i "no unique or exclusion constraint
    matching the ON CONFLICT specification" hatasıyla PATLIYORDU ve o anki
    TÜM buffer (satırlar) sessizce atılıyordu — eski kodda sadece tek satır
    `[checkpoint][WARN] ...: <mesaj>` stderr'e basılıyordu, verinin kendisi
    HİÇBİR YERDE görünmüyordu.
  - Bunu zaman damgalarıyla doğruladım: eksik aralığın komşuları
    (`solution_index=500000` ve `1000000`) `created_at=2026-09-16 14:42` /
    `14:44` — yani migration'ın canlı DB'ye uygulandığı 2026-09-18'den 2 gün
    ÖNCE yazılmış. Demek ki o dönemki koşu, 600k-900k aralığındaki
    checkpoint'leri üretti ama DB'ye YAZAMADI (constraint yoktu), sessizce
    kaybetti.
  - Migration artık DB'de canlı (bkz. `\d solving_checkpoint` çıktısı:
    `solving_checkpoint_full_state_key` UNIQUE constraint mevcut) ve kod bu
    constraint'in kolon listesiyle birebir uyumlu — bu spesifik hata sınıfı
    bir daha OLUŞMAZ. Ama geçmişteki boşluk kendiliğinden dolmaz.
  - Bu boşluk `Main.printCheckpointList` / "Sadece eksikleri goster" modunda
    zaten görülebiliyor (commit `762c88e` / `f1b0987` ile eklendi).
  - **Doldurmak istenirse:** "Checkpoint araligindan devam" ile `5-10`
    (checkpoint no; `solution_index=500000`'den `1000000`'e kadar) aralığını
    checkpoint DB kayıt modu AÇIK seçerek tekrar oynat — `PlayGame.resumeFrom
    /stopAfter` gerçek çözüm döngüsünü çalıştırır, ON CONFLICT DO NOTHING
    zaten var olan (500000, 1000000) satırlarını tekrar yazmaz, aradaki
    600k-900k'yı DOLDURUR.
- `path_len` 7x7 için HER ZAMAN 49'dur (7×7 = tam ızgara). Yani kullanıcının
  sorduğu `SELECT count(*) FROM solving_checkpoint WHERE path_len=49` sorgusu
  **7x7 verisini** döner (şu an 6055 satır), 6x6'yı DEĞİL.

### Genel sonuç

Algoritma **sapıtmıyor** — hem 6x6'da (küçük, tam koşu + 2x resume testi) hem
de 7x7'de (508 milyon çözümlük büyük koşu) restore edilip devam ettirilen
state, orijinal koşunun ürettiği değerlerle birebir eşleşiyor. Tespit edilen
tek sorun (7x7'deki 600k-900k boşluğu) bir **DB yazma hatasıydı, algoritma
hatası değildi** ve kök nedeni zaten commit `762c88e` ile düzeltilmişti; bu
oturumda ek olarak o hatanın bir daha SESSİZCE kaybolmaması için tanı
(diagnostics) eklendi (bkz. §4).

## 4. Kod değişikliği: başarısız checkpoint yazımı artık verisiyle birlikte loglanıyor

`src/persistence/checkpoint/Algo2CheckpointWriter.java` → `flush()` metodunun
catch bloğu. Eskiden SADECE:

```
[checkpoint][WARN] solving_checkpoint batch yazilamadi (N satir atlandi): <mesaj>
```

basıyordu — verinin kendisi kayboluyordu (yukarıdaki 7x7 boşluğunun nedeni
tam olarak buydu). Artık `flush()` başarısız olduğunda sırasıyla:

1. **Hata mesajı** — değişmedi, `logWarn(...)` ile aynı satır.
2. **Sebep zinciri** — `SQLException.getNextException()` zinciri tam olarak
   basılıyor (her halka için `SQLState`, `ErrorCode`, mesaj). Postgres'in
   gerçek nedeni (örn. constraint adı) genelde `BatchUpdateException`'ın
   ilk halkasında değil, zincirin devamında olur — hepsi artık görünür.
3. **DB'ye YAZILAMAYAN her satırın TAM verisi** — §2'deki alanların HEPSİ,
   insan okunur biçimde: `path` → `(x,y)` dizisine çözülmüş, `visited_dirs`
   → hex + set-bit sayısı, `one_way_list` → her navigasyon satır satır
   çözülmüş, `total_solved` "şüpheli - constraint'e dahil değil" notuyla.

Bu, kullanıcının `solving_checkpoint` tablosundaki en yakın satırla (örn.
`resolveGridMapId`'nin döndürdüğü `grid_map_id` + `solution_index` ile)
elle kıyaslayabilmesi için. Örnek DB tarafı sorgu:

```sql
SELECT solution_index, step, round_counter, total_solved,
       total_back_steps, dummy_back_steps, locked_back_lose,
       octet_length(path) path_b, octet_length(visited_dirs) vd_b,
       octet_length(one_way_list) owl_b
FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
WHERE g.row_size=6 AND g.col_size=6 AND c.algorithm_id=2
  AND c.solution_index = <konsolda basilan solution_index>;
```

Satır DB'de yoksa (yazım gerçekten başarısız olduysa), konsoldaki detaylı
çıktı DB'ye hiç ulaşmamış TEK kayıt kaynağıdır — kaybolmadan önce kopyalanıp
saklanmalı.

`mvn -q -o compile` ile derleme doğrulandı (hata yok).

## 5. Bu kıyaslamayı tekrar nasıl yaparsın

```sql
-- 1) Harita + algoritma bazında ilerleme özeti
SELECT g.row_size, g.col_size, c.algorithm_id, count(*),
       min(c.solution_index), max(c.solution_index), max(c.created_at)
FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
GROUP BY 1,2,3 ORDER BY 7 DESC;

-- 2) Bir haritanin TUM checkpoint'leri + boşluk taraması (elle)
SELECT c.solution_index, c.interval_size, c.round_counter, c.total_solved,
       c.total_back_steps, c.dummy_back_steps, c.created_at
FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
WHERE g.row_size=<R> AND g.col_size=<C> AND c.algorithm_id=2
ORDER BY c.solution_index;
-- Java tarafinda ayni tarama: Main.printCheckpointList(..., missingOnly=true)
-- ("Sadece eksikleri goster" secenegi) - konsoldan interaktif calistirilir.

-- 3) path_len'e gore hangi harita oldugunu dogrula (6x6 -> 36, 7x7 -> 49)
SELECT g.row_size, g.col_size, c.path_len, count(*)
FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
GROUP BY 1,2,3 ORDER BY 1,2;
```

Java tarafında canlı karşılaştırma: "Checkpoint araligindan devam" akışını
(`Main.runCheckpointRange`) DB kayıt modu **CHECKPOINT** ile çalıştır, bitince
konsolda basılan "---- Bu oturumda (current session, DB'ye kaydedilmez) ----"
bloğunu (`Current Total Solved/Step/Back Step/Dummy Back Step`) DB'de
gerçekten yazılan son satırla kıyasla — eşleşmeli (yukarıdaki 6x6/7x7
kanıtlarında eşleşti).

## 6. Bilinen sınırlar

- `path` 1 byte/kare tutuyor → hücre indeksi 0-255 → en fazla 15×15 harita
  (`CHECKPOINT.md`'de belgelenmiş, bu inceleme kapsamı dışı).
- Sadece Algoritma 2 checkpoint destekliyor.
- Geçmişteki 7x7 boşluğu (600k-900k) otomatik dolmaz; §3'teki "doldurmak
  istenirse" adımı elle çalıştırılmalı.
