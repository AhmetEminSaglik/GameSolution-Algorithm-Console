# postgre-connection.md — Paylasimli lokal PostgreSQL'e baglanma rehberi

Bu makinede, Docker icinde **tek** bir PostgreSQL calisiyor. Butun yan projeler
ayni instance'i paylasir ama **birbirinden izoledir**:

> **Her proje = kendi DATABASE'i + kendi ROLE'u.**
> Bir rol SADECE kendi database'ine baglanabilir. `avukat` rolu `pathexplorer`
> database'ine, `pathexplorer` rolu `avukat` database'ine **baglanamaz**.
> Yeni proje gelince yeni port / yeni container degil -> yeni rol + yeni db.

Bu dosyayi baska bir projedeki Claude session'ina verip "buna gore baglantiyi
kur" diyebilirsin.

---

## 0) Claude'a talimat (bunu oldugu gibi ilet)

> Bu makinede Docker'da paylasimli, projeler-arasi izole bir Postgres var
> (bilgiler asagida). Bu proje icin:
> 1. Container ayakta mi kontrol et; degilse `PG_COMPOSE_DIR`'deki compose'u kaldir.
> 2. `PG_COMPOSE_DIR/docker/db-provision.ps1 <proje-adi> [parola]` calistir
>    (bash: `db-provision.sh`). Bu, izole rol + database'i acar.
>    Proje adi verilmediyse klasor adindan turet; parola verilmediyse rol adiyla ayni yap.
> 3. Ciktidaki baglanti degerlerini bu projenin config'ine yaz
>    (`.env` / `application.properties` / neyse).
> 4. Varsa bu projenin `schema.sql`'ini yeni database'e uygula.
> `down -v`, `DROP DATABASE`, `DROP ROLE`, `ALTER ROLE ... SUPERUSER` gibi yikici
> komutlari BEN istemeden calistirma.

---

## 1) Sabit bilgiler

| Alan | Deger |
|---|---|
| Compose dizini (`PG_COMPOSE_DIR`) | `C:\btk\dev\GameSolution-Algorithm-Console` |
| Container adi | `dev-postgres`  *(compose'da `PG_CONTAINER`)* |
| Host | `localhost` |
| Host port | `5443`  *(compose'da `PG_PORT`; degismis olabilir — Adim 2'de dogrula)* |
| Container ic port | `5432` |
| Bootstrap superuser (admin) | `pathexplorer` / parola `pathexplorer` |
| Provision scripti | `docker/db-provision.ps1`  (PowerShell) / `docker/db-provision.sh` (bash) |
| Docker image | `postgres:16` |
| Volume | `dev_pgdata` |

> `pathexplorer` rolu bu instance'in **admin**'i (superuser). Sadece yeni
> rol/database acmak icin. Uygulama baglantisi olarak kullanilmaz — her proje
> kendi rolu ile baglanir. (`pathexplorer` projesinin kendi database'i de
> `pathexplorer`, ama prod'da onun rolu de superuser degil — bkz. postgre-prod.md.)

---

## 2) Container ayakta mi? Degilse baslat

```powershell
docker ps --filter name=dev-postgres --format "{{.Names}}  {{.Status}}  {{.Ports}}"
```

- Cikti varsa: `Ports` sutunundaki `0.0.0.0:XXXX->5432` -> gercek host port.
- Cikti yoksa baslat:

```powershell
cd "C:\btk\dev\GameSolution-Algorithm-Console"
docker compose up -d
docker compose ps
```

Hazir mi:

```powershell
docker exec dev-postgres pg_isready -U pathexplorer
```

---

## 3) Bu proje icin izole rol + database ac  (TEK KOMUT)

**PowerShell:**
```powershell
cd "C:\btk\dev\GameSolution-Algorithm-Console"
.\docker\db-provision.ps1 avukat            # parola = "avukat"
.\docker\db-provision.ps1 avukat s3cret     # parola = "s3cret"
```

**bash / WSL / Git Bash:**
```bash
cd /c/btk/dev/GameSolution-Algorithm-Console
./docker/db-provision.sh avukat
./docker/db-provision.sh avukat s3cret
```

Script ne yapar (hepsi idempotent — tekrar calistirmak guvenli):

1. `CREATE ROLE avukat LOGIN PASSWORD ... NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT`
2. `CREATE DATABASE avukat OWNER avukat`
3. `REVOKE CONNECT ON DATABASE avukat FROM PUBLIC` + `GRANT CONNECT ... TO avukat`
   -> baska hicbir rol bu database'e baglanamaz
4. `avukat` database'inde `public` schema'yi `avukat` rolune verir, PUBLIC'ten alir

Isim kurali: sadece `[a-z0-9_]`, kucuk harf/alt cizgi ile baslar.

> Var olan bir rolu superuser'sa script DOKUNMAZ (kendini kilitlemeyi onler).
> Var olan rolun **parolasini** degistirmek istersen Adim 6.

Dogrula:
```powershell
docker exec -it dev-postgres psql -U avukat -d avukat -c "\conninfo"
```

---

## 4) Baglanti degerleri

Script zaten bunlari basar. `5443` yerine Adim 2'deki portu, `avukat` yerine
kendi degerini koy.

| Format | Deger |
|---|---|
| Generic | `host=localhost port=5443 dbname=avukat user=avukat password=avukat` |
| libpq URI | `postgresql://avukat:avukat@localhost:5443/avukat` |
| JDBC | `jdbc:postgresql://localhost:5443/avukat` |
| SQLAlchemy | `postgresql+psycopg://avukat:avukat@localhost:5443/avukat` |

**.env**
```
DATABASE_URL=postgresql://avukat:avukat@localhost:5443/avukat
PGHOST=localhost
PGPORT=5443
PGDATABASE=avukat
PGUSER=avukat
PGPASSWORD=avukat
```

**Spring `application.properties`**
```
spring.datasource.url=jdbc:postgresql://localhost:5443/avukat
spring.datasource.username=avukat
spring.datasource.password=avukat
spring.datasource.driver-class-name=org.postgresql.Driver
```

**Node (pg)**
```
new Pool({ host: 'localhost', port: 5443, database: 'avukat', user: 'avukat', password: 'avukat' })
```

---

## 5) Bu projenin semasini yukle (varsa)

```powershell
docker exec -i dev-postgres psql -U avukat -d avukat -f - < path\to\schema.sql
# host'ta psql varsa:
psql "postgresql://avukat:avukat@localhost:5443/avukat" -f path\to\schema.sql
```

Kontrol:
```powershell
docker exec -it dev-postgres psql -U avukat -d avukat -c "\dt"
```

---

## 6) Port / parola degistirmek

- **Port:** `PG_COMPOSE_DIR\.env` icinde `PG_PORT=...` -> `docker compose up -d`
  (yeniden yaratir, veri kalir).
- **Bir proje rolunun parolasi:**
  ```powershell
  docker exec -it dev-postgres psql -U pathexplorer -d postgres -c "ALTER ROLE avukat PASSWORD 'yeni';"
  ```
- **Admin (`pathexplorer`) parolasi:** ayni sekilde `ALTER ROLE pathexplorer PASSWORD '...'`.
  `POSTGRES_USER`/`POSTGRES_PASSWORD` env'i SADECE volume ilk olusurken uygulanir.

---

## 7) Veri guvenligi

| Komut | Sonuc |
|---|---|
| `docker compose down` | Durur, **veri kalir** — bunu kullan |
| `docker compose down -v` | **Volume silinir, TUM projelerin verisi gider** — kullanma |
| `docker compose up -d` | Baslatir / ayar degisikligini uygular, veri kalir |

Tek bir projeyi silmek (digerlerine dokunmadan):
```powershell
docker exec -it dev-postgres psql -U pathexplorer -d postgres -c "DROP DATABASE avukat;" -c "DROP ROLE avukat;"
```

---

## 8) Prod

Ayni model prod'da da gecerli; degisenler ayri dosyada: **`postgre-prod.md`**.
Kisaca: `trust` auth yok, guclu parolalar, port'u disari acma, TLS, yedek, ve
provision scriptini `-PgHost`/`-PgPort` + `$env:PGADMIN_PASSWORD` ile calistirma.
