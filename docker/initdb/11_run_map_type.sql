-- run_map_type: kosunun tahtanin hangi baslangic karelerini gezdigi.
--   1 ALL    : tum N² baslangic karesi (simetri yok) - toplam dogrudan sayilir
--   2 UNIQUE : sadece simetrinin temel bolgesi (0 <= y <= x <= half);
--              toplam = Σ kare_sayisi × carpan (bkz. unique-area-calculation.md)
-- Uygulama bilinmeyen bir tipi ilk kosuda kendisi ekler (RunMapType).
--
-- solver_run ve solving_checkpoint'e run_map_type_id eklenir. checkpoint'te
-- tekillik kuralina da girer: ALL ve UNIQUE kosularin ilk karesi ayni durumlari
-- uretir; tip kuralda olmasa biri digerinin satirlarini "zaten var" diye atlatirdi.
--
-- Mevcut satirlar (2026-10-02): 6x6 checkpoint_version 1 kosusu tum tahtayi gezdi
-- (son solution_index 8.250.272 = 6x6 toplami) -> ALL. Digerleri UNIQUE
-- (5x5 v2, 6x6 v2, 7x7 v1+v2, 8x8 v1).
--
-- Var olan DB'ye elle uygula (cozucu DURMUSKEN - checkpoint ON CONFLICT kolon
-- listesi degisiyor):
--   docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/11_run_map_type.sql

BEGIN;

CREATE TABLE IF NOT EXISTS run_map_type (
    id          smallint PRIMARY KEY,
    code        varchar(16) NOT NULL UNIQUE,
    description text NOT NULL
);

INSERT INTO run_map_type (id, code, description) VALUES
    (1, 'ALL',    'Tum baslangic kareleri gezilir (simetri yok).'),
    (2, 'UNIQUE', 'Sadece simetrinin temel bolgesi gezilir (0 <= y <= x <= half); toplam carpanlarla bulunur.')
ON CONFLICT (id) DO NOTHING;

-- solver_run
ALTER TABLE solver_run ADD COLUMN IF NOT EXISTS run_map_type_id smallint REFERENCES run_map_type(id);
UPDATE solver_run SET run_map_type_id = 2 WHERE run_map_type_id IS NULL;
ALTER TABLE solver_run ALTER COLUMN run_map_type_id SET NOT NULL;

-- solving_checkpoint
ALTER TABLE solving_checkpoint ADD COLUMN IF NOT EXISTS run_map_type_id smallint REFERENCES run_map_type(id);
UPDATE solving_checkpoint SET run_map_type_id = 1
 WHERE run_map_type_id IS NULL AND checkpoint_version = 1
   AND grid_map_id = (SELECT id FROM grid_map WHERE row_size = 6 AND col_size = 6);
UPDATE solving_checkpoint SET run_map_type_id = 2 WHERE run_map_type_id IS NULL;
ALTER TABLE solving_checkpoint ALTER COLUMN run_map_type_id SET NOT NULL;

ALTER TABLE solving_checkpoint DROP CONSTRAINT IF EXISTS solving_checkpoint_full_state_key;
ALTER TABLE solving_checkpoint
    ADD CONSTRAINT solving_checkpoint_full_state_key
    UNIQUE (
        checkpoint_version, run_map_type_id, solution_index, grid_map_id, algorithm_id, interval_size,
        step, path_len, dir_count, path, visited_dirs, exit_situation, one_way_list,
        round_counter, total_back_steps, dummy_back_steps, locked_back_lose
    );

COMMENT ON COLUMN solver_run.run_map_type_id IS 'gezilen baslangic kareleri: 1 ALL, 2 UNIQUE (run_map_type)';
COMMENT ON COLUMN solving_checkpoint.run_map_type_id IS 'gezilen baslangic kareleri: 1 ALL, 2 UNIQUE (run_map_type)';

COMMIT;
