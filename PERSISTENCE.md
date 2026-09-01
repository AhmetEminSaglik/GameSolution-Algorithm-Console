# Çözümleri PostgreSQL'e Kaydetme

Cözücü bulduğu her çözümü, istenirse, batch'lerle PostgreSQL'e yazar.
DB **opsiyoneldir** — kapalıyken proje ve testler aynen çalışır.

## Kayıt modları

| mod | ne yapar | ne zaman |
|---|---|---|
| **flat** | her çözüm = 1 satır (`path_explorer_solution`, yön-kodlamalı `BYTEA`) | küçük haritalar (5x5, 6x6); yolların tekil erişimi |
| **trie** | parent-child ağaç (`solution_step`): **ortak önek 1 kez** | her boyut, özellikle büyük; "şu açılıştan kaç çözüm" analizi |
| **checkpoint** | çözüm saklanmaz; çözücü state'i her S çözümde bir (`solving_checkpoint`) — bkz. `CHECKPOINT.md` | **sadece Algoritma 2**; büyük çözüm sayısı |
| **all** | flat + trie + checkpoint | hepsi |

Mod seçimi:
- **Argüman varsa sessiz:** `--save=none|flat|trie|checkpoint|all` (`--save-db` = `--save=flat`;
  `--save=both` = flat+trie legacy; `--checkpoint` bayrağı herhangi bir modla birlikte checkpoint'i açar).
- **Argüman yoksa** konsoldan sorar: `0) yok  1) flat  2) trie  3) checkpoint  4) all`.
- `PATHEXPLORER_DB_ENABLED=1` = flat (prod için).

## Hızlı başlangıç

```bash
# 1) DB'yi kaldır (ilk seferde şema otomatik kurulur)
docker compose up -d

# 2) fat jar'ı üret
mvn -q package -DskipTests

# 3) DB kaydı AÇIK çalıştır (menü: rows/cols -> 2 Robot -> 2 SecondSolution)
java -jar target/game-solution-algorithm.jar --save-db
#   ya da:  PATHEXPLORER_DB_ENABLED=1 java -jar target/game-solution-algorithm.jar

# 4) açılış istatistiği
java -cp target/game-solution-algorithm.jar persistence.OpeningStats 5 5

# 5) elle sorgu
docker exec -it dev-postgres psql -U pathexplorer -d pathexplorer
```

DB kapalı (varsayılan) çalıştırma:
```bash
java -jar target/game-solution-algorithm.jar
```

## IntelliJ'den (jar almadan, test için)

`--save-db` bir **program argümanı** — yazınca DB kaydı açılır, yazmayınca kapalı.
Jar'a gerek yok:

1. Run config → **Edit Configurations…** → `Main.Main`
2. Şunlardan biri:
   - **Program arguments:** `--save-db`
   - **Environment variables:** `PATHEXPLORER_DB_ENABLED=1`
3. **Working directory** = proje kökü (varsayılan; `db.properties` oradan okunur).
4. Önce `docker compose up -d`, sonra normal Run.

Konsol çıktısında `DB kaydi: ACIK -> jdbc:...` görürsen bağlanmıştır.
`mvn exec:java -Dexec.args="--save-db"` de çalışır (menü etkileşimi Maven altında zahmetli).

## Nasıl çalışıyor

```
PlayGame ──(çözüm bulununca)──> SolutionSink.accept(GridPath)
                                   │
              ┌────────────────────┴────────────────────┐
       NoOpSolutionSink                        JdbcSolutionSink
       (hiçbir şey)                            buffer → 1000'de bir flush
                                               (addBatch/executeBatch/commit)
                                               oyun bitince son flush
                                               Ctrl+C → flush + status=ABORTED
```

- **Batch boyutu 1000** (`db.properties` → `batchSize`). Son eksik grup
  (örn. 12_400'ün son 400'ü) oyun bitince yazılır.
- **Yol kodlaması:** `PathCodec` — başlangıç hücresi + adım başına 3 bit yön.
  5x5 = 9 byte, 10x10 = 38 byte, 100x100 ≈ 3.7 KB. `(x<<4)|y` gibi 16x16'da
  tavan yapmaz.
- **`solver_run`** — koşu başına bir satır (row/col, algoritma, toplam çözüm,
  round counter, back step'ler, `status`: RUNNING → COMPLETED / ABORTED).
- **`path_explorer_solution`** — çözüm başına bir satır. `grid_size`
  (= row*1000+col) üzerinden LIST partition. `open1/2/3` = ilk 3 adımın hücre
  indeksi (indexli → "şu açılıştan kaç çözüm" sorgusu ucuz).

## Ayarlar

`db.properties` (kök) — lokal compose değerleri. **Ortam değişkeni her zaman ezer:**

| env | varsayılan |
|---|---|
| `PATHEXPLORER_DB_URL` | `jdbc:postgresql://localhost:5443/pathexplorer?reWriteBatchedInserts=true` |
| `PATHEXPLORER_DB_USER` | `pathexplorer` |
| `PATHEXPLORER_DB_PASSWORD` | `pathexplorer` |
| `PATHEXPLORER_DB_BATCH_SIZE` | `1000` |
| `PATHEXPLORER_DB_ENABLED` | (yok) — `1`/`true` ise DB kaydı açık |

> Port **5443** — paylaşımlı lokal `dev-postgres` (5432 native PostgreSQL, 5439 portfolio ile çakışmasın diye).

Remote/prod için `db.properties`'i değiştirme; env değişkeni geç:
```bash
PATHEXPLORER_DB_URL='jdbc:postgresql://sunucu:5432/pathexplorer' \
PATHEXPLORER_DB_USER=... PATHEXPLORER_DB_PASSWORD=... PATHEXPLORER_DB_ENABLED=1 \
java -jar target/game-solution-algorithm.jar
```

## Trie modu — parent-child ağaç

Her çözüm, kökten (adım 1 = başlangıç karesi) yaprağa (adım N) giden bir yol.
Aynı önek → aynı düğümler → **bir kez saklanır**. İlk 50 adımı paylaşan 6 milyon
çözüm için o 50 düğüm 1 kez.

**`grid_map`** ref tablosu: 5x5→1, 6x6→2, … 10x10→6, sonra 5x6→11. Aynı `(0,0)→(0,3)`
geçişi farklı haritalarda karışmasın diye trie `grid_map_id` + `run_id` ile ayrılır.

**`solution_step`** sütunları:
| sütun | anlam |
|---|---|
| `id` | client-assigned (parent id çocuktan önce lazım) |
| `parent_step_id` | üst düğüm (kök için NULL) |
| `step_no` | derinlik / adım no (1 = kök) |
| `x`, `y` | bu adımda bulunulan kare |
| `move_from_parent` | parent'tan gelen yön 0-7 (`PathCodec.DIRS`) |
| `solution_ordinal` | **"index"** — düğüm oluşturulduğunda `bulunan_çözüm + 1`. Çözüm bulunana kadar sabit; bulununca +1 |
| `subtree_solution_count` | **buradan geçen çözüm sayısı** — "şu açılıştan kaç çözüm" = bu sütunu oku, sayma yok |
| `is_leaf` | `step_no = rows*cols` (tam çözüm) |

**Nasıl çalışır:** Çözücünün DFS'i takip edilir. Aktif yol bellekte bir yığın.
Bir düğüm, altındaki tüm dallar tükenince (geri adımda) yazılır ve bellekten atılır
→ **bellek O(derinlik)**, grid boyutundan bağımsız. **Budama:** yalnızca en az bir
çözüme götüren düğümler saklanır (çıkmaz dallar yazılmaz).

**Sıkışma:** çözümler ne kadar ortak önek paylaşırsa o kadar. Ölçüldü:
- **5x5**: 150.609 düğüm (düz 12.400×24 = 297.600 hamle-slotu → ~2x). 5x5'te
  çözümler yayvan, önek paylaşımı az → kazanç mütevazı; bu boyutta `flat` daha küçük.
- Büyük gridlerde (derin ortak önek) kazanç **çok büyük**: senin 10x10 örneğinde
  ilk 50 adımı paylaşan milyonlarca çözüm için o 50 düğüm 1 kez.

**Örnek sorgular (trie):**
```sql
-- bir koşunun kök düğümleri (başlangıç kareleri) + altlarındaki çözüm sayısı
SELECT id, x, y, subtree_solution_count
  FROM solution_step
 WHERE run_id = :run AND parent_step_id IS NULL;

-- "adım1 (0,0), adım2 (0,3) açılışından kaç çözüm"
WITH r AS (SELECT id FROM solution_step
            WHERE run_id=:run AND parent_step_id IS NULL AND x=0 AND y=0)
SELECT s2.subtree_solution_count
  FROM solution_step s2, r
 WHERE s2.run_id=:run AND s2.parent_step_id=r.id AND s2.x=0 AND s2.y=3;

-- bir çözümü geri kur: recursive CTE ile yapraktan köke move'ları topla,
-- ters çevir, başlangıç karesinden PathCodec.DIRS[move] ile oyna.
```

## Ölçek uyarısı

| grid | çözüm sayısı | flat satır | trie düğüm (tahmini) |
|---|---|---|---|
| 5x5 | 12.400 | 12.400 | ~150.000 (ölçüldü) |
| 6x6 | ~8 milyon | ~8M | ??? (ortak öneke bağlı, muhtemelen çok daha az) |
| 7x7 (tüm başlangıçlar) | ~2 milyar | ~2G | ??? |
| 10x10 | trilyonlar | **imkansız** | derin ortak önekle çok daha küçük ama yine dev |

- **flat** ~7x7 üstünde pratik değil (her çözüm 1 satır).
- **trie** ortak öneki sıkıştırır — büyük gridlerde kazanç çok büyük. Ama dipteki
  dallanma hâlâ "farklı çözüm kuyruğu" sayısı kadar. Astronomik sayılarda bir
  sonraki adım *ortak alt-ağaçları da birleştirmek* (DAG / ZDD — Hamilton yolu
  sayımında araştırmada kullanılan yapı). Ayrı ve büyük iş; `solution_step` şeması
  ona doğru evrilebilir.
- **Partition:** `solution_step` `grid_map_id` ile LIST-partition'lı (6 harita +
  DEFAULT). Bir haritanın verisi çok büyürse o partition `run_id` ya da `step_no`
  aralığıyla alt-partition'lanır. Postgres tek partition'da milyarlarca satırı
  düzgün index ile taşır — "her milyar için yeni DB" gerekmez.

## Örnek sorgular

```sql
-- koşu özetleri
SELECT id, row_size, col_size, algorithm, total_solved, round_counter, status
FROM solver_run ORDER BY id DESC;

-- adım 1 = (0,0) olan çözüm sayısı  (open1 = 0*cols + 0 = 0)
SELECT COUNT(*) FROM path_explorer_solution
WHERE grid_size = 5005 AND open1 = 0;

-- adım1=(0,0) adım2=(0,3) olan çözümler  (open2 = 0*5 + 3 = 3)
SELECT COUNT(*) FROM path_explorer_solution
WHERE grid_size = 5005 AND open1 = 0 AND open2 = 3;

-- bir çözümün yolunu görmek: Java tarafında
--   PathCodec.decode(path, start_x, start_y, path_len)
```

## Thread notu

Paralel çözücü çalıştırırsan: her thread **kendi** `JdbcSolutionSink` örneğini
(dolayısıyla kendi `solver_run` satırını ve kendi buffer'ını) kullanmalı.
DB'nin IDENTITY sütunu eşzamanlı insert'leri kendisi halleder; ek koordinasyon
gerekmez.
