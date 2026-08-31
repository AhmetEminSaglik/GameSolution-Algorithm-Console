-- Trie (parent-child agac) ile cozum saklama semasi.
-- Ortak onek 1 kez saklanir; cozumlerin hepsi korunur (kok -> yaprak yolu).

-- =====================================================================
-- grid_map : harita tanimi (ref tablo). map id eslestirmesi.
-- =====================================================================
CREATE TABLE grid_map (
    id       SMALLINT PRIMARY KEY,
    row_size SMALLINT NOT NULL,
    col_size SMALLINT NOT NULL,
    UNIQUE (row_size, col_size)
);

INSERT INTO grid_map (id, row_size, col_size) VALUES
 (1, 5, 5), (2, 6, 6), (3, 7, 7), (4, 8, 8), (5, 9, 9), (6, 10, 10);
-- Yeni boyut gelince: INSERT INTO grid_map VALUES (11, 5, 6);  (5x6)

-- solver_run 01_schema.sql'de olusturuldu; trie icin 2 sutun ekle.
ALTER TABLE solver_run ADD COLUMN grid_map_id SMALLINT REFERENCES grid_map(id);
ALTER TABLE solver_run ADD COLUMN save_mode   VARCHAR(8);   -- flat | trie | both

-- =====================================================================
-- solution_step : trie dugumu = bir kismi cozum yolu adimi.
--   parent_step_id : ust dugum (kok icin NULL). Formal FK YOK (dev agac,
--                    uygulama butunlugu saglar; client-assigned id).
--   step_no        : derinlik / adim no (1 = baslangic karesi = kok).
--   x, y           : bu adimda bulunulan kare.
--   move_from_parent: parent'tan buraya gelen yon 0-7 (PathCodec.DIRS); kok NULL.
--   solution_ordinal: bu dugum olusturuldugunda "bulunan_cozum + 1". Cozum
--                     bulunana kadar sabit; bulununca +1. (kullanicinin "index"i)
--   subtree_solution_count : BURADAN GECEN tamamlanmis cozum sayisi.
--                            "su acilistan kac cozum" = bu sutunu oku.
--   is_leaf        : step_no = rows*cols  (tam cozum yapragi).
-- grid_map_id uzerinden LIST partition.
-- =====================================================================
CREATE TABLE solution_step (
    id                     BIGINT   NOT NULL,          -- client-assigned, run icinde essiz
    run_id                 BIGINT   NOT NULL,
    grid_map_id            SMALLINT NOT NULL,
    parent_step_id         BIGINT,
    step_no                SMALLINT NOT NULL,
    x                      SMALLINT NOT NULL,
    y                      SMALLINT NOT NULL,
    move_from_parent       SMALLINT,
    solution_ordinal       BIGINT   NOT NULL,
    subtree_solution_count BIGINT   NOT NULL DEFAULT 0,
    is_leaf                BOOLEAN  NOT NULL DEFAULT false,
    PRIMARY KEY (grid_map_id, run_id, id)
) PARTITION BY LIST (grid_map_id);

CREATE TABLE solution_step_m1      PARTITION OF solution_step FOR VALUES IN (1);
CREATE TABLE solution_step_m2      PARTITION OF solution_step FOR VALUES IN (2);
CREATE TABLE solution_step_m3      PARTITION OF solution_step FOR VALUES IN (3);
CREATE TABLE solution_step_m4      PARTITION OF solution_step FOR VALUES IN (4);
CREATE TABLE solution_step_m5      PARTITION OF solution_step FOR VALUES IN (5);
CREATE TABLE solution_step_m6      PARTITION OF solution_step FOR VALUES IN (6);
CREATE TABLE solution_step_default PARTITION OF solution_step DEFAULT;

CREATE INDEX ix_step_parent    ON solution_step (run_id, parent_step_id);
CREATE INDEX ix_step_map_depth ON solution_step (grid_map_id, step_no);
CREATE INDEX ix_step_run_leaf  ON solution_step (run_id, is_leaf);

-- =====================================================================
-- Ornek sorgular
-- =====================================================================
-- Bir kosunun kok dugumleri + altlarindaki cozum sayisi:
--   SELECT id, x, y, subtree_solution_count
--     FROM solution_step
--    WHERE run_id = :run AND parent_step_id IS NULL;
--
-- "adim1 (0,0) -> adim2 (0,3) acilisindan kac cozum":
--   WITH r AS (SELECT id FROM solution_step
--               WHERE run_id=:run AND parent_step_id IS NULL AND x=0 AND y=0),
--        s2 AS (SELECT id FROM solution_step
--                WHERE run_id=:run AND parent_step_id=(SELECT id FROM r)
--                  AND x=0 AND y=3)
--   SELECT subtree_solution_count FROM solution_step WHERE id=(SELECT id FROM s2);
--
-- Bir cozumu geri kur: yapraktan koke recursive CTE ile move'lari topla,
-- ters cevir, baslangic karesinden PathCodec.DIRS[move] ile oyna.
