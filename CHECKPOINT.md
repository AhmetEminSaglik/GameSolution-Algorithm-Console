# Checkpoint (Algoritma 2)

Her çözümü tek tek saklamak yerine, **deterministik** Algoritma 2 çözücüsünün
state'ini her `S` çözümde bir kaydeder. Sonra:
- **Devam et:** en son checkpoint'ten kaldığın yerden çözmeye devam.
- **Replay:** bir çözüm aralığını yeniden üret.

Sadece **Algoritma 2** (`SecondSolution_CalculateForwardAvailableWays`). Mevcut
tablolara (`solver_run`, `path_explorer_solution`, `solution_step`) dokunmaz.

Tablolar:
- **`solving_algorithm`** — çözüm algoritmaları referansı (id, code, name, description).
- **`solving_checkpoint`** — snapshot'lar; `algorithm_id` → `solving_algorithm`,
  `grid_map_id` → `grid_map`. Tekillik: `(grid_map_id, algorithm_id, solution_index)`
  — aynı ilerleme noktası tekrar yazılmaz (`ON CONFLICT DO NOTHING`).

> **Checkpoint noktaları:** her `interval` katı + **son çözüm** (koşu sonu / Ctrl+C'de
> interval'e denk gelmese bile). Her çözümde snapshot alınır, sadece bu noktalar DB'ye
> yazılır. DB hatası çözücüyü **durdurmaz** — `[checkpoint][WARN]` loglanır, devam edilir.

## Çalıştırma

Algoritma 2 seçince:
```
Baslangic:  1) Bastan basla   2) Checkpoint'ten devam et
```
- **1** → normal akış, ardından `DB kayit modu sec: 0) yok 1) flat 2) trie 3) checkpoint 4) all`
- **2** → bu harita + Algoritma 2 için DB'deki **tüm checkpoint'ler listelenir**, sıra
  no ile seçersin (boş = sonuncu). Seçilen state restore edilip oradan devam edilir;
  checkpoint kaydı açık kalır. Kayıt yoksa / `algorithm_version` uyuşmazsa / geçersiz
  seçim → baştan başlar (loglanır).
  ```
  Checkpoint'ler (5x5 algo2):
     1) #1000   round=82157    total_solved=1000   back=41066   ...
     2) #2000   round=153813   total_solved=2000   back=76893   ...
    ...
  Hangisinden devam? (sira no, bos = sonuncu):
  ```
  Sessiz: `--resume` → en son checkpoint.

Argüman / env (sessiz mod):
```
--resume                        # Algoritma 2'de checkpoint'ten devam
--save=checkpoint | --save=all
--checkpoint                    # herhangi bir save moduyla birlikte checkpoint'i açar
PATHEXPLORER_CHECKPOINT_ENABLED=1
```

Aralık (`db.properties`):
```
checkpoint.interval.5x5=1000
checkpoint.interval.6x6=10000
checkpoint.interval.default=100000
```
`PATHEXPLORER_CHECKPOINT_INTERVAL=5000` tüm boyutlar için ezer.

## Tabloları kur

`docker/initdb/*.sql` sadece volume ilk oluşurken çalışır. `dev_pgdata` zaten varsa
**sırayla** elle uygula (bağımlılık: `grid_map` ← `03_trie.sql`):
```
docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/02_solving_algorithm.sql
docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/03_trie.sql   # grid_map yoksa
docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/04_solving_checkpoint.sql
```

## DB'de gör

```sql
SELECT id, code, name, left(description, 60) FROM solving_algorithm;

-- harita + algoritma bazında ilerleme
SELECT g.row_size, g.col_size, c.algorithm_id, count(*),
       min(c.solution_index), max(c.solution_index), max(c.created_at)
FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
GROUP BY 1,2,3 ORDER BY 7 DESC;

-- 5x5 + algo2 checkpoint'leri
SELECT c.solution_index, c.step, c.exit_situation,
       octet_length(c.path) path_b, octet_length(c.visited_dirs) vdirs_b,
       octet_length(c.one_way_list) owl_b,
       c.round_counter, c.total_solved, c.total_back_steps, c.dummy_back_steps
FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
WHERE g.row_size=5 AND g.col_size=5 AND c.algorithm_id=2
ORDER BY c.solution_index;
```

## `solving_checkpoint` kolonları

| kolon | ne |
|---|---|
| `checkpoint_id` | UUID, satır kimliği (PK, `gen_random_uuid()`). |
| `solving_run_id` | UUID, bu satırı **hangi çalışma yazdı** (audit). Sorgu anahtarı değil. |
| `solution_index` | "Bu state `#solution_index`'i yeni üretti." |
| `grid_map_id` | → `grid_map(id)`. Harita buradan; `row_size`/`col_size` yok. |
| `algorithm_id` | → `solving_algorithm(id)`. `BaseSolution.getSolutionCreatedOrder()`. |
| `algorithm_version` | `Algo2Snapshot.ALGORITHM_VERSION`. Karar mantığı değişince artır → eski checkpoint geçersiz (devam/replay reddeder). |
| `interval_size` | Bu koşuda kaç çözümde bir alındı. |
| `step`, `path_len` | DFS derinliği (çözüm anında grid kare sayısı). |
| `dir_count` | Yön sayısı (LocationsList = 9). `visited_dirs` bit indeksleme için. |
| `path` | `path[k]` = (k+1). adımın hücre indeksi `x*col+y`, 1 byte/kare. |
| `visited_dirs` | `visitedDirections[step][dir]` bitset; bit = `step*dir_count + dir`. Backtrack cursor'ı. |
| `exit_situation` | `RoadMemory.exitSituation` (0 EXIT_FREE / 1 EXIT_LOCATED). |
| `one_way_list` | `RoadMemory.oneWayNumbersList`: `int count`, sonra her nav → `int step, int oneWayValue, int compulsoryDirId(-1=null), byte exitLocatedHere`. |
| `round_counter` (+`_overlong`) | `Game.roundCounter`. `_overlong` = `Long.MAX_VALUE` taşma sayacı → gerçek = `overlong*MAX + round_counter`. |
| `total_solved` (+`_overlong`) | `Score.totalGameFinishedScore` — o ana kadar bulunan çözüm. |
| `total_back_steps` | `Score.counterTotalBackStep` (isim `solver_run` ile aynı). |
| `dummy_back_steps` | `Score.counterOfDummyBackMove`. Çözüm bulmadan atılan "boşa" geri adım. |
| `locked_back_lose` | `Score.lockedCounterOfMovingBackLose`. `dummy_back_steps` sayacının kapısı. |
| `square_total_solved` | `Player.squareTotalSolvedValue`. O anki başlangıç karesinden bulunan (kare değişince 0'lanır). |

Metrikler rapor sürekliliği içindir. Bir checkpoint **anlık görüntüdür** — koşu sonu
toplamları her zaman son checkpoint'ten büyüktür (son çözümden sonra arama tükenene
kadar geri sarma devam eder).

## Faz B — replay / verify CLI

`persistence.checkpoint.ReplayMain` (anahtar: harita + algoritma):

```
ReplayMain replay <RxC> <algo> [from] [to]        # replay 5x5 2 5000 5000
ReplayMain verify <RxC> <algo> <checkpointIndex>  # verify 5x5 2 12000
```

- **replay**: `from`/`to` verilmezse konsoldan sorar. **`to` boş ya da `0` → sona
  kadar** (çözümler 1'den başladığı için 0 karışıklık yapmaz). `<= from` olan son
  checkpoint otomatik yüklenir (`Algo2StateRestorer` tahta + `visitedDirections` +
  `RoadMemory` + sayaçları kurar), Algoritma 2 çözücüsü o state'ten `PlayGame`
  döngüsünün çekirdeğiyle ileri oynatılır; `[from, to]` aralığındaki her çözüm
  **tam yol + adım-numaralı ASCII grid** ile yazdırılır (streaming — büyük aralık
  belleği şişirmez).
- **verify**: checkpoint'ten bir sonrakine oynatır, varılan state'i (tüm sayaçlar +
  `path`/`visited_dirs`/`one_way_list` baytları) o satırla karşılaştırır. **Boş çıktı =
  tam eşleşme → determinizm doğrulandı.** Fark listelerse gizli bir non-determinizm var.

Kısıtlar:
- `algorithm_id != 2` veya `algorithm_version` kod ile uyuşmuyorsa reddeder.
- İlk (`interval`) checkpoint'inden önceki çözümler replay edilemez (o aralık için
  checkpoint yok).
- Replay/devam çalışma dizininde `Solution-2-<RxC>_*` dosyalarını yazar (oyun
  makinesinin yan etkisi; determinizmi etkilemez).

## Sınır

`path` 1 byte/kare → hücre indeksi 0..255 → en fazla 15x15. `grid_map`'te olmayan
boyut → checkpoint devre dışı kalır (`[checkpoint][WARN]` loglanır, çözücü çalışır).
