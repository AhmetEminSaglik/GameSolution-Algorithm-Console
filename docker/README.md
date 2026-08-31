# PathExplorer — cozum kaydi icin PostgreSQL

Bu klasor, cozucunun buldugu cozumleri kaydettigi ayri bir PostgreSQL'i
Docker Compose ile ayaga kaldirir. Ana projenin remote Postgres'iyle ilgisi yok.

## Kullanim

```bash
# baslat (ilk seferde 01_schema.sql otomatik calisir)
docker compose up -d

# durumu gor
docker compose ps

# psql ile baglan
docker exec -it pathexplorer-db psql -U pathexplorer -d pathexplorer

# durdur (veri kalir)
docker compose down

# tamamen sifirla (veri + sema silinir, sonraki up'ta sema yeniden kurulur)
docker compose down -v
```

## Baglanti bilgisi

| | |
|---|---|
| host | `localhost` |
| port | `5442` (5432 native PostgreSQL ile cakisiyordu; doluysa `docker-compose.yml` + `db.properties` degistir) |
| database | `pathexplorer` |
| user | `pathexplorer` |
| password | `pathexplorer` |
| JDBC URL | `jdbc:postgresql://localhost:5442/pathexplorer?reWriteBatchedInserts=true` |

Java uygulamasi bu degerleri `db.properties` dosyasindan veya `PATHEXPLORER_DB_*`
ortam degiskenlerinden okur (bkz. Faz 5).

## Sema ozeti

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
