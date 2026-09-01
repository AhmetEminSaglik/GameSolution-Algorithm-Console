-- Algoritma 2 (SecondSolution_CalculateForwardAvailableWays) icin CHECKPOINT/snapshot.
-- Amac: her cozumu tek tek saklamak yerine, deterministik cozucunun state'ini her S
-- cozumde bir kaydetmek. Bir cozum araligi istendiginde: <= araligin basi olan son
-- checkpoint'i yukle, algoritmayi ileri oynat.
--
-- Mevcut tablolara (solver_run, path_explorer_solution, grid_map, solution_step)
-- DOKUNMAZ. Tamamen bagimsiz.
--
-- Bu dosya sadece volume ILK olusurken calisir. dev_pgdata zaten varsa elle uygula:
--   docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer \
--     < docker/initdb/02_algo2_checkpoint.sql

CREATE TABLE IF NOT EXISTS algo2_checkpoint (
    run_id          UUID     NOT NULL,          -- bir cozucu kosusunu tanimlar (uygulama uretir)
    solution_index  BIGINT   NOT NULL,          -- "bu state #solution_index'i YENI uretti"
    row_size        SMALLINT NOT NULL,
    col_size        SMALLINT NOT NULL,
    algo_version    SMALLINT NOT NULL,          -- Algoritma 2 karar mantigi surumu; degisince eski cp gecersiz
    interval_size   INTEGER  NOT NULL,          -- bu kosuda kac cozumde bir checkpoint alindi

    -- --- deterministik replay icin cekirdek state ---
    step            SMALLINT NOT NULL,          -- o anki DFS derinligi (cozum aninda = row*col)
    path_len        SMALLINT NOT NULL,          -- dolu kare sayisi (= step)
    dir_count       SMALLINT NOT NULL,          -- yon sayisi (LocationsList boyutu, 9)
    path            BYTEA    NOT NULL,          -- path[k] = (k+1). adimin hucre indeksi (x*col_size + y), 1 byte/kare
    visited_dirs    BYTEA    NOT NULL,          -- visitedDirections[step][dir] bitset, bit = step*dir_count + dir
    exit_situation  SMALLINT NOT NULL,          -- RoadMemory.exitSituation (0 EXIT_FREE / 1 EXIT_LOCATED)
    one_way_list    BYTEA    NOT NULL,          -- RoadMemory.oneWayNumbersList: int count, sonra her nav: int step, int oneWayValue, int compulsoryDirId(-1=null), byte exitLocatedHere

    -- --- metrik (rapor surekliligi; replay dogrulugu icin sart degil) ---
    round_counter          BIGINT  NOT NULL,
    round_counter_overlong INTEGER NOT NULL DEFAULT 0,
    total_solved           BIGINT  NOT NULL,
    total_solved_overlong  INTEGER NOT NULL DEFAULT 0,
    total_back_step        BIGINT  NOT NULL,
    dummy_back_move        BIGINT  NOT NULL,
    locked_back_lose       BOOLEAN NOT NULL,
    square_total_solved    INTEGER NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, solution_index)
);

-- "su kosunun, su cozumden onceki son checkpoint'i" sorgusu:
--   SELECT * FROM algo2_checkpoint
--    WHERE run_id = :run AND solution_index <= :target
--    ORDER BY solution_index DESC LIMIT 1;
-- PK (run_id, solution_index) bu sorgu icin yeterli (index-only, geriye tarama).

-- Kosu listesi / son durum:
CREATE INDEX IF NOT EXISTS ix_algo2_cp_run_created
    ON algo2_checkpoint (run_id, created_at);
