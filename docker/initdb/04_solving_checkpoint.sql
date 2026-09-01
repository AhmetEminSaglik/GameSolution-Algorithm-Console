-- Cozucu CHECKPOINT/snapshot'lari. Her cozumu tek tek saklamak yerine, deterministik
-- cozucunun state'ini her S cozumde bir kaydeder. Bir cozum araligi istendiginde:
-- <= araligin basi olan son checkpoint yuklenip algoritma ileri oynatilir.
--
-- SADECE Algoritma 2 destekleniyor (state RoadMemory'ye bagli). algorithm_id ile
-- solving_algorithm'e, grid_map_id ile grid_map'e baglanir. row_size / col_size YOK
-- (harita grid_map'ten). "current" (dinamik satir/sutun) modu gerekirse ileride
-- current_row / current_col + current_prefix eklenir.
--
-- Bagimliliklar (once bunlar): grid_map -> 03_trie.sql,  solving_algorithm -> 02_solving_algorithm.sql
-- dev_pgdata zaten varsa elle uygula:
--   docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/04_solving_checkpoint.sql

CREATE TABLE IF NOT EXISTS solving_checkpoint (
    checkpoint_id     UUID     NOT NULL DEFAULT gen_random_uuid(),  -- satir kimligi
    solving_run_id    UUID     NOT NULL,          -- bir cozucu kosusunu gruplar (uygulama uretir)
    solution_index    BIGINT   NOT NULL,          -- "bu state #solution_index'i YENI uretti"

    grid_map_id       SMALLINT NOT NULL REFERENCES grid_map(id),
    algorithm_id      SMALLINT NOT NULL REFERENCES solving_algorithm(id),
    algorithm_version SMALLINT NOT NULL,          -- karar mantigi surumu; degisince eski checkpoint gecersiz
    interval_size     INTEGER  NOT NULL,          -- bu kosuda kac cozumde bir alindi

    -- --- deterministik replay cekirdegi ---
    step              SMALLINT NOT NULL,          -- DFS derinligi (cozum aninda = grid kare sayisi)
    path_len          SMALLINT NOT NULL,          -- dolu kare sayisi (= step)
    dir_count         SMALLINT NOT NULL,          -- yon sayisi (LocationsList = 9); visited_dirs bit indeksleme icin
    path              BYTEA    NOT NULL,          -- path[k] = (k+1). adimin hucre indeksi (x*col + y), 1 byte/kare
    visited_dirs      BYTEA    NOT NULL,          -- visitedDirections[step][dir] bitset; bit = step*dir_count + dir
    exit_situation    SMALLINT NOT NULL,          -- RoadMemory.exitSituation (0 EXIT_FREE / 1 EXIT_LOCATED)
    one_way_list      BYTEA    NOT NULL,          -- RoadMemory.oneWayNumbersList: int count, sonra her nav: int step, int oneWayValue, int compulsoryDirId(-1=null), byte exitLocatedHere

    -- --- metrik (rapor surekliligi; replay dogrulugu icin sart degil) ---
    -- isimler solver_run ile ayni: total_back_steps, dummy_back_steps
    round_counter          BIGINT  NOT NULL,
    round_counter_overlong INTEGER NOT NULL DEFAULT 0,   -- round_counter Long.MAX_VALUE'yi asinca +1 (round = overlong*MAX + round_counter)
    total_solved           BIGINT  NOT NULL,
    total_solved_overlong  INTEGER NOT NULL DEFAULT 0,   -- total_solved Long.MAX_VALUE'yi asinca +1
    total_back_steps       BIGINT  NOT NULL,             -- Score.counterTotalBackStep
    dummy_back_steps       BIGINT  NOT NULL,             -- Score.counterOfDummyBackMove ("bosa" geri adim)
    locked_back_lose       BOOLEAN NOT NULL,             -- Score.lockedCounterOfMovingBackLose (dummy sayacinin kapisi)
    square_total_solved    INTEGER NOT NULL,             -- Player.squareTotalSolvedValue (o anki baslangic karesinden bulunan)

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (checkpoint_id),
    -- Ilerleme (harita + algoritma) bazinda tekil. Ayni checkpoint tekrar YAZILMAZ
    -- (ON CONFLICT DO NOTHING). "Devam et" bu anahtardan okur.
    UNIQUE (grid_map_id, algorithm_id, solution_index)
);

-- audit: bir satiri ilk hangi calisma yazdi
CREATE INDEX IF NOT EXISTS ix_solving_cp_run_created
    ON solving_checkpoint (solving_run_id, created_at);

-- "5x5 + algoritma 2 icin en son checkpoint" (devam et):
--   SELECT c.* FROM solving_checkpoint c JOIN grid_map g ON g.id = c.grid_map_id
--    WHERE g.row_size=5 AND g.col_size=5 AND c.algorithm_id=2
--    ORDER BY c.solution_index DESC LIMIT 1;
