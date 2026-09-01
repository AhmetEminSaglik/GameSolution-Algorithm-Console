# Checkpoint (Algoritma 2)

Her çözümü tek tek saklamak yerine, **deterministik** Algoritma 2 çözücüsünün
state'ini her `S` çözümde bir kaydeder. Bir çözüm aralığı istendiğinde: `<= aralığın
başı` olan son checkpoint'i yükle, algoritmayı ileri oynat.

Sadece **Algoritma 2** (`SecondSolution_CalculateForwardAvailableWays`). Mevcut
tablolara (`solver_run`, `path_explorer_solution`, `solution_step`) dokunmaz.

Tablolar:
- **`solving_algorithm`** — çözüm algoritmaları referansı (id, code, name, description).
- **`solving_checkpoint`** — snapshot'lar; `algorithm_id` → `solving_algorithm`,
  `grid_map_id` → `grid_map` ile eşleşir. `row_size`/`col_size` yok.

> **Durum:** Faz A = state'i DB'ye **yazma** (bu commit). Faz B = geri yükleme +
> replay (sonraki adım). Şu an checkpoint'leri DB'de görebilirsin ama henüz
> onlardan çözüm yeniden üretmiyoruz.

## Aç

Konsol menüsü:
```
DB kayit modu sec:  0) yok   1) flat   2) trie   3) checkpoint   4) all
```
`3` = sadece checkpoint, `4` = flat + trie + checkpoint.

Argüman / env ile:
```
--save=checkpoint      # ya da --save=all
--checkpoint           # herhangi bir save moduyla birlikte checkpoint'i de açar
PATHEXPLORER_CHECKPOINT_ENABLED=1
```

Aralık (`db.properties`):
```
checkpoint.interval.5x5=1000
checkpoint.interval.6x6=10000
checkpoint.interval.default=100000
```
`PATHEXPLORER_CHECKPOINT_INTERVAL=5000` tüm boyutlar için ezer.

Konsolda `Checkpoint: ACIK  run=<uuid>  her 1000 cozumde bir` → aktif.

## Tabloları kur

`docker/initdb/*.sql` sadece volume ilk oluşurken çalışır. `dev_pgdata` zaten varsa
**sırayla** elle uygula (bağımlılık: `grid_map` ← `03_trie.sql`, `solving_algorithm`
← `02_solving_algorithm.sql`):
```
docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/02_solving_algorithm.sql
docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/03_trie.sql   # grid_map yoksa
docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/04_solving_checkpoint.sql
```
`03_trie.sql` zaten uygulandıysa tekrar çalıştırma (idempotent değil). Sadece
`grid_map` tablosunun var olduğundan emin ol.

## DB'de gör

```sql
-- algoritmalar
SELECT id, code, name, left(description, 60) FROM solving_algorithm;

-- koşular
SELECT solving_run_id, algorithm_id, grid_map_id, count(*),
       min(solution_index), max(solution_index), max(created_at)
FROM solving_checkpoint GROUP BY 1,2,3 ORDER BY 7 DESC;

-- bir koşunun checkpoint'leri
SELECT solution_index, step, exit_situation,
       octet_length(path) AS path_b, octet_length(visited_dirs) AS vdirs_b,
       octet_length(one_way_list) AS owl_b,
       round_counter, total_solved, total_back_step, dummy_back_move
FROM solving_checkpoint WHERE solving_run_id = '...' ORDER BY solution_index;

-- "3200. çözümden önceki son checkpoint"
SELECT * FROM solving_checkpoint
WHERE solving_run_id = '...' AND solution_index <= 3200
ORDER BY solution_index DESC LIMIT 1;
```

## `solving_checkpoint` kolonları

| kolon | ne |
|---|---|
| `checkpoint_id` | UUID, satır kimliği (PK, `gen_random_uuid()`). |
| `solving_run_id` | UUID, bir çözücü koşusunu gruplar (uygulama üretir). |
| `solution_index` | "Bu state `#solution_index`'i yeni üretti." `UNIQUE (solving_run_id, solution_index)`. |
| `grid_map_id` | → `grid_map(id)`. Harita buradan; `row_size`/`col_size` yok. |
| `algorithm_id` | → `solving_algorithm(id)`. `BaseSolution.getSolutionCreatedOrder()`. |
| `algorithm_version` | `Algo2Snapshot.ALGORITHM_VERSION`. Karar mantığı değişince artır → eski checkpoint geçersiz. |
| `interval_size` | Bu koşuda kaç çözümde bir alındı. |
| `step`, `path_len` | DFS derinliği (çözüm anında grid kare sayısı). |
| `dir_count` | Yön sayısı (LocationsList = 9). `visited_dirs` bit indeksleme için. |
| `path` | `path[k]` = (k+1). adımın hücre indeksi `x*col+y`, 1 byte/kare. |
| `visited_dirs` | `visitedDirections[step][dir]` bitset; bit = `step*dir_count + dir`. Backtrack cursor'ı. |
| `exit_situation` | `RoadMemory.exitSituation` (0 EXIT_FREE / 1 EXIT_LOCATED). |
| `one_way_list` | `RoadMemory.oneWayNumbersList`: `int count`, sonra her nav → `int step, int oneWayValue, int compulsoryDirId(-1=null), byte exitLocatedHere`. |
| `round_counter(+_overlong)`, `total_solved(+_overlong)`, `total_back_step`, `dummy_back_move`, `locked_back_lose`, `square_total_solved` | Metrik — rapor sürekliliği için; replay doğruluğu için şart değil. |

## Sınır

`path` 1 byte/kare → hücre indeksi 0..255 → en fazla 15x15. Daha büyüğü gerekirse
`short`'a çıkarılır. `grid_map`'te olmayan boyut → `Algo2CheckpointWriter` hata verir
(önce `grid_map`'e satır ekle).
