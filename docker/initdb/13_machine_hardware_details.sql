-- machine: hizi etkileyen ek donanim alanlari + lookup tablolari
-- (bkz. persistence.MachineInfo). Iki bilgisayari karsilastirirken:
--   os_family     : Windows / macOS / Linux - zamanlayici, guc yonetimi ve JVM
--                   derlemesi farkli; ayni donanimda bile sure degisebilir
--   chassis_type  : Kasa / Laptop / Mini PC / All-in-One - laptop'ta sogutma ve
--                   guc siniri (ozellikle pilde) sureyi uzatir
--   cpu_vendor    : Intel / AMD / Apple
--   cpu_arch      : x86_64 / aarch64 (Apple M-serisi ARM)
--   cpu_l2_mb, ram_rated_mts (nominal; ram_mts = calisan), ram_manufacturer,
--   ram_part, gpu_model / gpu_vram_mb (cozucu GPU KULLANMIYOR, kayit icin),
--   disk_type / disk_bus (SSD/HDD, NVMe/SATA - DB yazma hizi), os_build
-- Lookup'larda olmayan bir deger gelirse uygulama satiri kendisi ekler.
-- Mevcut machine satirlarinin bos alanlarini uygulama sonraki kosuda doldurur.
--
--   docker exec -i dev-postgres psql -U pathexplorer -d pathexplorer < docker/initdb/13_machine_hardware_details.sql

BEGIN;

CREATE TABLE IF NOT EXISTS os_family (
    id   smallint PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    name text NOT NULL
);
INSERT INTO os_family (id, code, name) VALUES
    (1, 'WINDOWS', 'Windows'),
    (2, 'MACOS',   'macOS'),
    (3, 'LINUX',   'Linux')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS chassis_type (
    id   smallint PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    name text NOT NULL
);
INSERT INTO chassis_type (id, code, name) VALUES
    (1, 'DESKTOP',    'Kasa (masaustu)'),
    (2, 'LAPTOP',     'Laptop'),
    (3, 'MINI_PC',    'Mini PC'),
    (4, 'ALL_IN_ONE', 'All-in-One')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS cpu_vendor (
    id   smallint PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    name text NOT NULL
);
INSERT INTO cpu_vendor (id, code, name) VALUES
    (1, 'INTEL', 'Intel'),
    (2, 'AMD',   'AMD'),
    (3, 'APPLE', 'Apple')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE machine
    ADD COLUMN IF NOT EXISTS os_family_id     smallint REFERENCES os_family(id),
    ADD COLUMN IF NOT EXISTS chassis_type_id  smallint REFERENCES chassis_type(id),
    ADD COLUMN IF NOT EXISTS cpu_vendor_id    smallint REFERENCES cpu_vendor(id),
    ADD COLUMN IF NOT EXISTS cpu_arch         text,
    ADD COLUMN IF NOT EXISTS cpu_l2_mb        smallint,
    ADD COLUMN IF NOT EXISTS ram_rated_mts    smallint,
    ADD COLUMN IF NOT EXISTS ram_manufacturer text,
    ADD COLUMN IF NOT EXISTS ram_part         text,
    ADD COLUMN IF NOT EXISTS gpu_model        text,
    ADD COLUMN IF NOT EXISTS gpu_vram_mb      integer,
    ADD COLUMN IF NOT EXISTS disk_type        text,
    ADD COLUMN IF NOT EXISTS disk_bus         text,
    ADD COLUMN IF NOT EXISTS os_build         text;

COMMIT;
