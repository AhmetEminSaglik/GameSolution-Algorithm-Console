-- Trie (parent-child agac) ile cozum saklama semasi.
-- Ortak onek 1 kez saklanir; cozumlerin hepsi korunur (kok -> yaprak yolu).
--
-- Tekillik: bir harita + algoritma icin trie TEK kez saklanir. Cozucu deterministik
-- oldugu icin dugum id'leri her kosuda ayni; tekrar calistirma ON CONFLICT DO NOTHING
-- ile atlanir (count(*) sabit kalir).
--
-- Bu dosya idempotent: tekrar uygulanabilir. Bagimlilik: solving_algorithm (02_*).

-- =====================================================================
-- grid_map : harita tanimi (ref tablo).
-- =====================================================================
CREATE TABLE IF NOT EXISTS grid_map (
    id       SMALLINT PRIMARY KEY,
    row_size SMALLINT NOT NULL,
    col_size SMALLINT NOT NULL,
    UNIQUE (row_size, col_size)
);

INSERT INTO grid_map (id, row_size, col_size) VALUES
 (1, 5, 5), (2, 6, 6), (3, 7, 7), (4, 8, 8), (5, 9, 9), (6, 10, 10)
ON CONFLICT (id) DO NOTHING;

-- solver_run 01_schema.sql'de olusturuldu; trie icin 2 sutun ekle.
ALTER TABLE solver_run ADD COLUMN IF NOT EXISTS grid_map_id SMALLINT REFERENCES grid_map(id);
ALTER TABLE solver_run ADD COLUMN IF NOT EXISTS save_mode   VARCHAR(8);   -- flat | trie | both | all

-- =====================================================================
-- solution_step : trie dugumu = bir kismi cozum yolu adimi.
--   id              : client-assigned. Cozucu deterministik -> her kosuda ayni.
--   run_id          : bu dugumu ILK yazan kosu (audit; kimligin parcasi DEGIL).
--   algorithm_id    : -> solving_algorithm(id). Ayni harita farkli algoritma = ayri trie.
--   parent_step_id  : ust dugum (kok icin NULL). Formal FK YOK (client-assigned id).
--   step_no         : derinlik (1 = baslangic karesi = kok).
--   move_from_parent: parent'tan buraya gelen yon 0-7 (PathCodec.DIRS); kok NULL.
--   solution_ordinal: bu dugum olusturuldugunda "bulunan_cozum + 1".
--   subtree_solution_count : BURADAN GECEN tamamlanmis cozum sayisi ("su acilistan kac cozum").
--   is_leaf         : step_no = rows*cols (tam cozum yapragi).
-- grid_map_id uzerinden LIST partition.  Tekillik: (grid_map_id, algorithm_id, id).
-- =====================================================================
CREATE TABLE IF NOT EXISTS solution_step (
    id                     BIGINT   NOT NULL,
    run_id                 BIGINT   NOT NULL,
    grid_map_id            SMALLINT NOT NULL,
    algorithm_id           SMALLINT NOT NULL REFERENCES solving_algorithm(id),
    parent_step_id         BIGINT,
    step_no                SMALLINT NOT NULL,
    x                      SMALLINT NOT NULL,
    y                      SMALLINT NOT NULL,
    move_from_parent       SMALLINT,
    solution_ordinal       BIGINT   NOT NULL,
    subtree_solution_count BIGINT   NOT NULL DEFAULT 0,
    is_leaf                BOOLEAN  NOT NULL DEFAULT false,
    PRIMARY KEY (grid_map_id, algorithm_id, id)
) PARTITION BY LIST (grid_map_id);

CREATE TABLE IF NOT EXISTS solution_step_m1      PARTITION OF solution_step FOR VALUES IN (1);
CREATE TABLE IF NOT EXISTS solution_step_m2      PARTITION OF solution_step FOR VALUES IN (2);
CREATE TABLE IF NOT EXISTS solution_step_m3      PARTITION OF solution_step FOR VALUES IN (3);
CREATE TABLE IF NOT EXISTS solution_step_m4      PARTITION OF solution_step FOR VALUES IN (4);
CREATE TABLE IF NOT EXISTS solution_step_m5      PARTITION OF solution_step FOR VALUES IN (5);
CREATE TABLE IF NOT EXISTS solution_step_m6      PARTITION OF solution_step FOR VALUES IN (6);
CREATE TABLE IF NOT EXISTS solution_step_default PARTITION OF solution_step DEFAULT;

CREATE INDEX IF NOT EXISTS ix_step_parent    ON solution_step (grid_map_id, algorithm_id, parent_step_id);
CREATE INDEX IF NOT EXISTS ix_step_map_depth ON solution_step (grid_map_id, step_no);
CREATE INDEX IF NOT EXISTS ix_step_map_leaf  ON solution_step (grid_map_id, algorithm_id, is_leaf);

-- =====================================================================
-- Ornek sorgular  (artik run_id degil, algorithm_id ile)
-- =====================================================================
-- 5x5 (grid_map_id=1) + algoritma 2'nin kok dugumleri + altlarindaki cozum sayisi:
--   SELECT id, x, y, subtree_solution_count
--     FROM solution_step
--    WHERE grid_map_id = 1 AND algorithm_id = 2 AND parent_step_id IS NULL;
--
-- "adim1 (0,0) -> adim2 (0,3) acilisindan kac cozum":
--   WITH r AS (SELECT id FROM solution_step
--               WHERE grid_map_id=1 AND algorithm_id=2 AND parent_step_id IS NULL
--                 AND x=0 AND y=0),
--        s2 AS (SELECT id FROM solution_step
--                WHERE grid_map_id=1 AND algorithm_id=2
--                  AND parent_step_id=(SELECT id FROM r) AND x=0 AND y=3)
--   SELECT subtree_solution_count FROM solution_step
--    WHERE grid_map_id=1 AND algorithm_id=2 AND id=(SELECT id FROM s2);
--
-- Bir cozumu geri kur: yapraktan koke recursive CTE ile move'lari topla, ters cevir,
-- baslangic karesinden PathCodec.DIRS[move] ile oyna.
--
-- Not: bir kosu ABORTED bitmisse ust dugumlerin subtree_solution_count'u eksik
-- kalabilir; sonraki TAM kosu ON CONFLICT DO NOTHING ile bunu duzeltmez. Temiz
-- deger icin once o (grid_map_id, algorithm_id) satirlarini sil, sonra tam kosu.
