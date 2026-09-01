# Paylasimli lokal gelistirme PostgreSQL'i

`docker-compose.yml` **tek** bir PostgreSQL container'i (`dev-postgres`) ayaga
kaldirir. Butun yan projeler bu instance'i paylasir; her proje **ayri bir port
degil, ayri bir database** kullanir. Portfolio projesinin Postgres'iyle ilgisi
yok, ona dokunulmuyor.

## Kullanim

```bash
# baslat (ilk seferde initdb/*.sql otomatik calisir)
docker compose up -d

docker compose ps

# psql ile baglan
docker exec -it dev-postgres psql -U pathexplorer -d pathexplorer

# durdur — VERI KALIR (bunu kullan)
docker compose down

# volume'u sil — TUM VERI GIDER, sonraki up'ta sema sifirdan kurulur (dikkat!)
docker compose down -v
```

## Baglanti bilgisi

| | |
|---|---|
| host | `localhost` |
| port | `5443` |
| database | `pathexplorer` |
| user | `pathexplorer` |
| password | `pathexplorer` |
| JDBC URL | `jdbc:postgresql://localhost:5443/pathexplorer?reWriteBatchedInserts=true` |

Java uygulamasi bu degerleri `db.properties` dosyasindan veya `PATHEXPLORER_DB_*`
ortam degiskenlerinden okur.

## pgAdmin'e ekleme

Servers → sag tik → Register → Server
- **General → Name:** `local-dev` (ya da istedigin bir isim)
- **Connection:** Host `localhost`, Port `5443`, Maintenance DB `pathexplorer`,
  Username `pathexplorer`, Password `pathexplorer`

## Yeni proje ekleme (ileride)

Container zaten ayaktaysa `initdb/*.sql` bir daha calismaz. Yeni projeyi elle ekle:

```bash
# 1) rol + database
docker exec -it dev-postgres psql -U pathexplorer -d pathexplorer -c \
  "CREATE ROLE yeniproje LOGIN PASSWORD 'yeniproje';"
docker exec -it dev-postgres psql -U pathexplorer -d pathexplorer -c \
  "CREATE DATABASE yeniproje OWNER yeniproje;"

# 2) o projenin semasini yukle
docker exec -i dev-postgres psql -U yeniproje -d yeniproje < yol/schema.sql
```

`pathexplorer` kullanicisi bu instance'in superuser'i oldugu icin yeni
rol/database olusturabilir.

## Sema ozeti (pathexplorer)

- **`solver_run`** — bir cozucu kosusunun run-seviyesi metrikleri (bir satir/kosu).
  `status`: `RUNNING` → `COMPLETED` (normal bitis) / `ABORTED` (Ctrl+C).
- **`path_explorer_solution`** — her bulunan cozum bir satir. `grid_size`
  (= `row_size*1000 + col_size`) uzerinden LIST partition. Bilinen boyutlar
  (5x5, 5x6, 6x6, 7x7, 10x10) icin ayri partition; digerleri `_default`'a.
  - `path BYTEA` — yon-kodlamasi, adim basina 3 bit (`PathCodec`).
  - `open1/2/3` — ilk 3 adimin hucre indeksi (`x*col_size + y`), indexli.
    "Su acilistan kac cozum var" sorgusu icin (`GROUP BY open1, open2, open3`).
  - `created_at` — `timestamptz`, mikrosaniye tavani (nanosaniye YOK).
    Gercek siralama `solution_index`.
- **`grid_map`**, **`solution_step`** (+ `_m1.._m6` partition) — trie (parent-child
  agac) ile cozum saklama (`03_trie.sql`).

## Ornek sorgular

```sql
-- kosu ozeti
SELECT id, row_size, col_size, algorithm, total_solved, elapsed_ms, status
FROM solver_run ORDER BY id DESC;

-- 5x5'te acilis (ilk 3 adim) bazinda cozum sayisi
SELECT open1, open2, open3, COUNT(*)
FROM path_explorer_solution
WHERE grid_size = 5005
GROUP BY open1, open2, open3
ORDER BY COUNT(*) DESC
LIMIT 20;

-- adim 1 = (0,0) [open1 = 0] olan cozumlerin sayisi
SELECT COUNT(*) FROM path_explorer_solution
WHERE grid_size = 5005 AND open1 = 0;
```
