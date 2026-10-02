-- path_explorer_solution (flat kayit): grid_size (row*1000+col) yerine grid_map_id.
--
-- Neden: flat tablo grid_map'ten once kuruldu ve kendi grid kodunu (5005, 6006)
-- kullaniyordu; trie (solution_step), checkpoint ve backup ise grid_map_id ile
-- calisiyor. Partition anahtari ALTER ile degistirilemedigi icin tablo yeniden
-- kurulur; partition'lar solution_step gibi _m<grid_map_id> adini alir.
--
-- VERI SILER: tablo DROP edilip yeniden olusturulur. 2026-10-02'de tablo bostu
-- (5x5/6x6 flat verisi kullanicinin istegiyle silindi, 7x7+ flat hic yoktu).
-- Dolu bir DB'de calistirmadan once veriyi yedekle.
--
-- Var olan DB'ye elle uygula:
--   docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/10_path_explorer_solution_grid_map.sql

BEGIN;

-- Koruma: tabloda veri varsa HICBIR SEY yapmadan dur (yanlislikla tekrar
-- calistirilirsa flat veriyi silmesin).
DO $$
BEGIN
  IF to_regclass('public.path_explorer_solution') IS NOT NULL
     AND EXISTS (SELECT 1 FROM path_explorer_solution) THEN
    RAISE EXCEPTION 'path_explorer_solution bos degil - once yedekle ve bosalt, sonra tekrar calistir';
  END IF;
END $$;

DROP TABLE IF EXISTS path_explorer_solution CASCADE;   -- partition'lari da gider

CREATE TABLE path_explorer_solution (
    id             BIGINT   GENERATED ALWAYS AS IDENTITY,
    public_id      UUID     NOT NULL,
    solver_run_id  BIGINT   NOT NULL REFERENCES solver_run(id),
    solution_index BIGINT   NOT NULL,
    grid_map_id    SMALLINT NOT NULL REFERENCES grid_map(id),
    row_size       SMALLINT NOT NULL,   -- okunabilirlik icin (grid_map'ten de cikar)
    col_size       SMALLINT NOT NULL,
    start_x        SMALLINT NOT NULL,
    start_y        SMALLINT NOT NULL,
    path_len       SMALLINT NOT NULL,
    path           BYTEA    NOT NULL,
    open1          SMALLINT NOT NULL,
    open2          SMALLINT,
    open3          SMALLINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, grid_map_id),
    UNIQUE (public_id, grid_map_id)
) PARTITION BY LIST (grid_map_id);

-- grid_map: 1..6 = 5x5..10x10 (bkz. 03_trie.sql). Yeni boyut DEFAULT'a duser.
CREATE TABLE path_explorer_solution_m1      PARTITION OF path_explorer_solution FOR VALUES IN (1);
CREATE TABLE path_explorer_solution_m2      PARTITION OF path_explorer_solution FOR VALUES IN (2);
CREATE TABLE path_explorer_solution_m3      PARTITION OF path_explorer_solution FOR VALUES IN (3);
CREATE TABLE path_explorer_solution_m4      PARTITION OF path_explorer_solution FOR VALUES IN (4);
CREATE TABLE path_explorer_solution_m5      PARTITION OF path_explorer_solution FOR VALUES IN (5);
CREATE TABLE path_explorer_solution_m6      PARTITION OF path_explorer_solution FOR VALUES IN (6);
CREATE TABLE path_explorer_solution_default PARTITION OF path_explorer_solution DEFAULT;

CREATE INDEX ix_pes_run     ON path_explorer_solution (solver_run_id);
CREATE INDEX ix_pes_opening ON path_explorer_solution (grid_map_id, open1, open2, open3);

COMMIT;
