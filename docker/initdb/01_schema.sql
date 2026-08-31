-- PathExplorer cozum kaydi semasi. Postgres 16.
-- Bu dosya docker-entrypoint-initdb.d icinde -> container ILK kez olusurken
-- otomatik calisir. Sonradan degistirirsen "docker compose down -v" gerekir.

-- =====================================================================
-- solver_run : bir cozucu kosusunun run-seviyesi metrikleri.
-- Milyarlarca cozum satirinda tekrar edilmemesi icin ayri tutulur.
-- =====================================================================
CREATE TABLE solver_run (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        UUID        NOT NULL UNIQUE,
    row_size         SMALLINT    NOT NULL,
    col_size         SMALLINT    NOT NULL,
    algorithm        VARCHAR(64) NOT NULL,
    total_solved     BIGINT,
    round_counter    BIGINT,
    total_back_steps BIGINT,
    dummy_back_steps BIGINT,
    elapsed_ms       BIGINT,
    status           VARCHAR(16) NOT NULL DEFAULT 'RUNNING',  -- RUNNING | COMPLETED | ABORTED
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ
);

-- =====================================================================
-- path_explorer_solution : her bulunan cozum bir satir.
--   path  : yon-kodlamasi (adim basina 3 bit, bit-packed).  PathCodec.
--   open1/2/3 : ilk 3 adimin hucre indeksi (x*col_size + y).  "Su acilistan
--               kac cozum var" sorgusu icin indexli, BYTEA taramadan.
--   created_at : Postgres timestamptz -> mikrosaniye tavani (nanosaniye YOK).
--                Gercek siralama = solution_index.
-- grid_size uzerinden LIST partition (row_size*1000 + col_size).
-- =====================================================================
-- grid_size = row_size * 1000 + col_size  (uygulama insert sirasinda doldurur;
-- Postgres generated column'u partition key olarak kabul etmiyor).
CREATE TABLE path_explorer_solution (
    id             BIGINT   GENERATED ALWAYS AS IDENTITY,
    public_id      UUID     NOT NULL,
    solver_run_id  BIGINT   NOT NULL REFERENCES solver_run(id),
    solution_index BIGINT   NOT NULL,
    row_size       SMALLINT NOT NULL,
    col_size       SMALLINT NOT NULL,
    grid_size      INTEGER  NOT NULL,
    start_x        SMALLINT NOT NULL,
    start_y        SMALLINT NOT NULL,
    path_len       SMALLINT NOT NULL,
    path           BYTEA    NOT NULL,
    open1          SMALLINT NOT NULL,
    open2          SMALLINT,
    open3          SMALLINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, grid_size),
    UNIQUE (public_id, grid_size)
) PARTITION BY LIST (grid_size);

-- Bilinen grid boyutlari icin partition. Yeni boyut gelince tek satir eklenir;
-- eklenmezse DEFAULT partition'a duser (yine calisir, sadece daha az izole).
CREATE TABLE path_explorer_solution_5x5   PARTITION OF path_explorer_solution FOR VALUES IN (5005);
CREATE TABLE path_explorer_solution_5x6   PARTITION OF path_explorer_solution FOR VALUES IN (5006);
CREATE TABLE path_explorer_solution_6x6   PARTITION OF path_explorer_solution FOR VALUES IN (6006);
CREATE TABLE path_explorer_solution_7x7   PARTITION OF path_explorer_solution FOR VALUES IN (7007);
CREATE TABLE path_explorer_solution_10x10 PARTITION OF path_explorer_solution FOR VALUES IN (10010);
CREATE TABLE path_explorer_solution_default PARTITION OF path_explorer_solution DEFAULT;

CREATE INDEX ix_pes_run     ON path_explorer_solution (solver_run_id);
CREATE INDEX ix_pes_opening ON path_explorer_solution (grid_size, open1, open2, open3);

-- =====================================================================
-- ILERISI ICIN NOT (simdi olusturulmuyor):
-- 7x7 ustunde her cozumu saklamak pratik degil (trilyon satir). O noktada
-- yalniz-aggregate moda gecilir:
--   CREATE TABLE solution_count_by_opening (
--       row_size SMALLINT, col_size SMALLINT,
--       open1 SMALLINT, open2 SMALLINT, open3 SMALLINT,
--       cnt BIGINT,
--       PRIMARY KEY (row_size, col_size, open1, open2, open3)
--   );
-- Sema buna eklemeli gecise uygun; path_explorer_solution tablosu aynen kalir.
-- =====================================================================
