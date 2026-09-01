# Checkpoint (Algoritma 2)

Her çözümü tek tek saklamak yerine, **deterministik** Algoritma 2 çözücüsünün
state'ini her `S` çözümde bir kaydeder. Bir çözüm aralığı istendiğinde: `<= aralığın
başı` olan son checkpoint'i yükle, algoritmayı ileri oynat.

Sadece **Algoritma 2** (`SecondSolution_CalculateForwardAvailableWays`). Mevcut
tablolara (`solver_run`, `path_explorer_solution`, `grid_map`, `solution_step`)
dokunmaz — bağımsız `algo2_checkpoint` tablosu.

> **Durum:** Faz A = state'i DB'ye **yazma** (bu commit). Faz B = geri yükleme +
> replay (sonraki adım). Şu an checkpoint'leri DB'de görebilirsin ama henüz
> onlardan çözüm yeniden üretmiyoruz.

## Aç

`db.properties`:
```
checkpoint.enabled=true
checkpoint.interval.5x5=1000
checkpoint.interval.6x6=10000
checkpoint.interval.default=100000
```
veya çalıştırırken: `java -jar ... --checkpoint` / `PATHEXPLORER_CHECKPOINT_ENABLED=1`.
`PATHEXPLORER_CHECKPOINT_INTERVAL=5000` tüm boyutlar için aralığı ezer.

Konsolda `Checkpoint: ACIK  run=<uuid>  her 1000 cozumde bir` görürsen aktif.

## Tabloyu kur

`docker/initdb/02_algo2_checkpoint.sql` sadece volume ilk oluşurken çalışır.
`dev_pgdata` zaten varsa elle uygula:
```
docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer \
  < docker/initdb/02_algo2_checkpoint.sql
```

## DB'de gör

```sql
-- kaç checkpoint, hangi koşu
SELECT run_id, count(*), min(solution_index), max(solution_index), max(created_at)
FROM algo2_checkpoint GROUP BY run_id ORDER BY 5 DESC;

-- bir koşunun checkpoint'leri
SELECT solution_index, step, exit_situation, octet_length(path) AS path_b,
       octet_length(visited_dirs) AS vdirs_b, octet_length(one_way_list) AS owl_b,
       round_counter, total_solved, total_back_step, dummy_back_move
FROM algo2_checkpoint
WHERE run_id = '...'
ORDER BY solution_index;

-- "3200. çözümden önceki son checkpoint"
SELECT * FROM algo2_checkpoint
WHERE run_id = '...' AND solution_index <= 3200
ORDER BY solution_index DESC LIMIT 1;
```

## `algo2_checkpoint` kolonları

| kolon | ne |
|---|---|
| `run_id`, `solution_index` | PK. "Bu state `#solution_index`'i yeni üretti." |
| `algo_version` | `Algo2Snapshot.ALGO_VERSION`. Algoritma 2 karar mantığı değişince artır → eski checkpoint'ler geçersiz. |
| `interval_size` | Bu koşuda kaç çözümde bir alındı. |
| `step`, `path_len` | DFS derinliği (çözüm anında `row*col`). |
| `dir_count` | Yön sayısı (LocationsList = 9). `visited_dirs` bit indeksleme için. |
| `path` | `path[k]` = (k+1). adımın hücre indeksi `x*col+y`, 1 byte/kare. |
| `visited_dirs` | `visitedDirections[step][dir]` bitset; bit = `step*dir_count + dir`. Backtrack cursor'ı. |
| `exit_situation` | `RoadMemory.exitSituation` (0 EXIT_FREE / 1 EXIT_LOCATED). |
| `one_way_list` | `RoadMemory.oneWayNumbersList`: `int count`, sonra her nav → `int step, int oneWayValue, int compulsoryDirId(-1=null), byte exitLocatedHere`. |
| `round_counter(+_overlong)`, `total_solved(+_overlong)`, `total_back_step`, `dummy_back_move`, `locked_back_lose`, `square_total_solved` | Metrik — rapor sürekliliği için; replay doğruluğu için şart değil. |

## Sınır

`path` 1 byte/kare → hücre indeksi 0..255 → en fazla 15x15. Daha büyüğü gerekirse
`short`'a çıkarılır.
