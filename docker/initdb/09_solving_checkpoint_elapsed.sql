-- solving_checkpoint.elapsed: bir onceki checkpoint satirindan bu satira kadar
-- SADECE cozme icin gecen sure (Algo2CheckpointWriter, System.nanoTime ile olcer).
-- Her kayitta sifirlanir. Process kapali kaldigi sure (restart arasi) dahil DEGIL -
-- created_at farki o bosluklari da icerir, elapsed icermez. Resume sonrasi ilk
-- satirin elapsed'i o process'in ilk cozumunden itibaren sayilir.
--
-- Tip interval: psql'de direkt okunur (00:01:34.512), sum()/avg() calisir,
-- saniye lazim olursa extract(epoch FROM elapsed).
-- Tekillik kuralinda YOK (resume'da ayni state farkli sureyle gelirse yine atlanir).
-- Eski satirlarda NULL.
--
-- Var olan DB'ye elle uygula (sadece kolon ekler, satirlara dokunmaz; calisan eski
-- kod kolon listesini acikca verdigi icin etkilenmez):
--   docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/09_solving_checkpoint_elapsed.sql
--
-- Ornek:
--   SELECT solution_index, elapsed,
--          sum(elapsed) OVER (ORDER BY solution_index) AS toplam_elapsed
--     FROM solving_checkpoint WHERE grid_map_id = 3 AND checkpoint_version = 2
--    ORDER BY solution_index;

ALTER TABLE solving_checkpoint
    ADD COLUMN IF NOT EXISTS elapsed interval;

COMMENT ON COLUMN solving_checkpoint.elapsed IS 'onceki checkpoint satirindan bu satira kadar cozme suresi (restart arasi bekleme haric); her kayitta sifirlanir';
