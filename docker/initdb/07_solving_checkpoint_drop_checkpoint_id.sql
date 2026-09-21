-- 06_solving_checkpoint_ids_and_checkpoint_no.sql'de eklenen okunabilir int
-- checkpoint_id kolonu simdilik GERI ALINDI - ihtiyac netlesince ayri bir
-- migration'la tekrar eklenebilir (o zaman checkpoint_no'yu mu id olarak
-- kullanmak, yoksa ayri bir int id mi eklemek daha dogru, karar verilecek).
--
-- checkpoint_uuid (PK) ve checkpoint_no (solution_index/interval_size,
-- hesaplanmis) AYNEN KALIYOR - sadece checkpoint_id kaldiriliyor.
--
-- Var olan bir DB'ye elle uygula:
--   docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/07_solving_checkpoint_drop_checkpoint_id.sql

ALTER TABLE solving_checkpoint
    DROP CONSTRAINT IF EXISTS solving_checkpoint_checkpoint_id_key;

ALTER TABLE solving_checkpoint
    DROP COLUMN IF EXISTS checkpoint_id;
