-- solving_checkpoint: (grid_map_id, algorithm_id, solution_index) yerine TAM STATE
-- bazinda tekillik + algorithm_version ve square_total_solved kolonlarinin kaldirilmasi.
--
-- algorithm_version KALDIRILDI: projede bir algoritmanin ic mantigi GUNCELLENMIYOR -
-- yeni/duzeltilmis bir algoritma her zaman solving_algorithm'e YENI bir satir (yeni
-- algorithm_id) olarak ekleniyor, o yuzden versiyon takibine gerek yok.
--
-- square_total_solved KALDIRILDI: su an gereksiz, ileride farkli bir yontemle
-- (checkpoint path'inin ilk hucresinden SQL ile) hesaplanacak.
--
-- total_solved KOLON OLARAK KALIYOR (resume sonrasi sayaclarin dogru surdugunu
-- kontrol amacli) ama artik CONSTRAINT'TE DEGIL - solution_index'le bire-bir
-- orantili oldugu icin ayirt edicilik katmiyor.
--
-- Amac (genel): checkpoint'ten resume edip ayni araligi TEKRAR oynatinca
-- (algoritma deterministik) ayni solution_index icin ayni state uretilirse
-- ON CONFLICT DO NOTHING sessizce atlar (beklenen); FARKLI bir state uretilirse
-- (bug/resume hatasi) YENI bir satir olarak eklenir - anomali sessizce kaybolmaz.
--
-- initdb SADECE volume ilk olusurken calisir. Var olan bir DB'ye elle uygula:
--   docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/05_solving_checkpoint_full_state_unique.sql
--
-- ONEMLI SIRALAMA: bu ALTER, calisan cozucunun INSERT'inde kullandigi kolon listesini
-- ve ON CONFLICT hedefini degistirir. Algo2CheckpointWriter.java bu kolonlarla
-- BIREBIR eslesmeli. O yuzden:
--   1) Once cozucuyu DURDUR (eski kod calisirken bu ALTER'i UYGULAMA).
--   2) Bu migration'i uygula.
--   3) Guncellenmis kodla (yeni INSERT/ON CONFLICT listesiyle) derleyip tekrar baslat.
-- Aksi halde: calisan eski kod artik var olmayan kolonlara/constraint'e yazmaya
-- calisir, HER checkpoint yazimi hatayla basarisiz olur.

ALTER TABLE solving_checkpoint
    DROP CONSTRAINT IF EXISTS solving_checkpoint_grid_map_id_algorithm_id_solution_index_key;

ALTER TABLE solving_checkpoint
    DROP COLUMN IF EXISTS algorithm_version,
    DROP COLUMN IF EXISTS square_total_solved;

ALTER TABLE solving_checkpoint
    ADD CONSTRAINT solving_checkpoint_full_state_key
    UNIQUE (
        solution_index, grid_map_id, algorithm_id, interval_size,
        step, path_len, dir_count, path, visited_dirs, exit_situation, one_way_list,
        round_counter, total_back_steps, dummy_back_steps, locked_back_lose
    );
