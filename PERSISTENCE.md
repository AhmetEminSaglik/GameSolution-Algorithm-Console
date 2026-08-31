# Çözümleri PostgreSQL'e Kaydetme

Cözücü bulduğu her çözümü, istenirse, batch'lerle PostgreSQL'e yazar.
DB **opsiyoneldir** — kapalıyken proje ve testler aynen çalışır.

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
docker exec -it pathexplorer-db psql -U pathexplorer -d pathexplorer
```

DB kapalı (varsayılan) çalıştırma:
```bash
java -jar target/game-solution-algorithm.jar
```

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
| `PATHEXPLORER_DB_URL` | `jdbc:postgresql://localhost:5442/pathexplorer?reWriteBatchedInserts=true` |
| `PATHEXPLORER_DB_USER` | `pathexplorer` |
| `PATHEXPLORER_DB_PASSWORD` | `pathexplorer` |
| `PATHEXPLORER_DB_BATCH_SIZE` | `1000` |
| `PATHEXPLORER_DB_ENABLED` | (yok) — `1`/`true` ise DB kaydı açık |

> Port **5442** — 5432 bu makinede native PostgreSQL ile çakışıyordu.

Remote/prod için `db.properties`'i değiştirme; env değişkeni geç:
```bash
PATHEXPLORER_DB_URL='jdbc:postgresql://sunucu:5432/pathexplorer' \
PATHEXPLORER_DB_USER=... PATHEXPLORER_DB_PASSWORD=... PATHEXPLORER_DB_ENABLED=1 \
java -jar target/game-solution-algorithm.jar
```

## Ölçek uyarısı

| grid | çözüm sayısı | satır büyüklüğü tahmini |
|---|---|---|
| 5x5 | 12.400 | ~3.8 MB |
| 6x6 | ~8 milyon | ~2-3 GB |
| 7x7 (tüm başlangıçlar) | ~2 milyar | ~TB'lar |
| 10x10 | trilyonlar | **saklanamaz** |

**~7x7 üstünde her çözümü saklamak pratik değil.** O noktada yalnız-aggregate
moda geçilmeli: `(rows, cols, open1, open2, open3) → count` tablosu. Şema buna
**eklemeli** geçişe uygun (`docker/initdb/01_schema.sql` sonundaki yorum).
`path_explorer_solution` aynen kalır, sadece dolu tutulmaz.

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
