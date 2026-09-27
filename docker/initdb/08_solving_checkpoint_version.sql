-- checkpoint_version tablosu + solving_checkpoint.checkpoint_version / first_location.
--
-- checkpoint_version: checkpoint yazma mantiginin surumu. Ayni grid'i bastan
-- kosarken eski kayitlari SILMEDEN yenisini ayirmak ve farki gormek icin.
-- Yeni satirlar db.properties'teki checkpoint.version degerini yazar; listeleme/
-- resume sadece o surumun satirlarini okur. Mantik degisirse yeni surum satiri ekle.
-- first_location: cozumun baslangic karesi "x-y" (orn. "0-0"). 1. surum satirlarinda
-- NULL - onlar icin get_byte(path,0) kullan (hucre = x*col + y).
--
-- Var olan DB'ye elle uygula:
--   docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/08_solving_checkpoint_version.sql
--
-- ONEMLI SIRALAMA (bkz. 05): calisan ESKI kod ON CONFLICT'te eski kolon listesini
-- kullanir; constraint degisince her checkpoint yazimi hata verir. Once cozucuyu
-- DURDUR, sonra uygula, sonra yeni kodla baslat.

BEGIN;

CREATE TABLE IF NOT EXISTS checkpoint_version (
    id          smallint PRIMARY KEY,
    description text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

INSERT INTO checkpoint_version (id, description) VALUES
    (1, 'Ilk surum: her interval cozumde bir checkpoint + kosu kapanisinda son durum. '
        'Baslangic karesi (ilk lokasyon) degisimi kaydedilmez; kare sayaci resume''da sifirlaniyordu.'),
    (2, 'Ilk lokasyon (baslangic karesi) degisince onceki karenin son cozumu de checkpoint olarak kaydedilir; '
        'kare basina cozum sayisi DB''den tam cikar. first_location yazilir. '
        'Surum 1 ile karismasin diye bastan kosuldu.')
ON CONFLICT (id) DO NOTHING;

-- ara deneme kolonu (checkpoint_version'a donustu)
ALTER TABLE solving_checkpoint
    DROP COLUMN IF EXISTS run_version;

ALTER TABLE solving_checkpoint
    ADD COLUMN IF NOT EXISTS checkpoint_version smallint,
    ADD COLUMN IF NOT EXISTS first_location varchar(7);

-- Bu migration'dan onceki tum satirlar 1. surum mantigiyla yazildi.
UPDATE solving_checkpoint SET checkpoint_version = 1 WHERE checkpoint_version IS NULL;

ALTER TABLE solving_checkpoint
    ALTER COLUMN checkpoint_version SET NOT NULL;

ALTER TABLE solving_checkpoint
    DROP CONSTRAINT IF EXISTS solving_checkpoint_checkpoint_version_fkey;
ALTER TABLE solving_checkpoint
    ADD CONSTRAINT solving_checkpoint_checkpoint_version_fkey
    FOREIGN KEY (checkpoint_version) REFERENCES checkpoint_version(id);

ALTER TABLE solving_checkpoint
    DROP CONSTRAINT IF EXISTS solving_checkpoint_full_state_key;
ALTER TABLE solving_checkpoint
    ADD CONSTRAINT solving_checkpoint_full_state_key
    UNIQUE (
        checkpoint_version, solution_index, grid_map_id, algorithm_id, interval_size,
        step, path_len, dir_count, path, visited_dirs, exit_situation, one_way_list,
        round_counter, total_back_steps, dummy_back_steps, locked_back_lose
    );

COMMENT ON COLUMN solving_checkpoint.checkpoint_version IS 'checkpoint yazma surumu, bkz. checkpoint_version tablosu (db.properties checkpoint.version)';
COMMENT ON COLUMN solving_checkpoint.first_location IS 'baslangic karesi "x-y"; 1. surumde NULL (get_byte(path,0) kullan)';

COMMIT;
