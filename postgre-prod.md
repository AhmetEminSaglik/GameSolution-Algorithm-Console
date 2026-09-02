# postgre-prod.md — Ayni kurulumu prod'a tasima

`postgre-connection.md` **lokal** kurulumu anlatir (Docker `dev-postgres`, `trust`
auth, zayif parolalar — lokalde sorun degil). Bu dosya: **prod'da neyi
degistirmen gerektigi**. Model ayni kalir:

> Her proje = kendi DATABASE'i + kendi ROLE'u. Rol sadece kendi db'sine baglanir.
> Provision scripti (`docker/db-provision.*`) prod'da da calisir — sadece
> `-PgHost/-PgPort` + `$env:PGADMIN_PASSWORD` verirsin (asagida).

---

## 0) Lokal vs Prod — tek bakista

| Konu | Lokal (`postgre-connection.md`) | Prod |
|---|---|---|
| Auth | `trust` (parolasiz `docker exec`) | `scram-sha-256`, her baglanti parolali |
| Parolalar | `avukat/avukat` gibi | rol basina uzun rastgele, secret manager'da |
| Admin rol | `pathexplorer` (superuser) | ayri `dbadmin` superuser; `pathexplorer` de dahil hicbir app rolu superuser degil |
| Port | `5443` host'a acik (`0.0.0.0`) | disari **kapali**; sadece app agi / VPC / `127.0.0.1` |
| TLS | yok | `sslmode=require` (tercihen `verify-full`) |
| Sema | `initdb/*.sql` (ilk up'ta) | migration araci (Flyway/Liquibase/alembic), her deploy |
| Yedek | yok (lokal) | otomatik `pg_dump` + WAL arsivleme/PITR, restore testi |
| Instance | Docker `postgres:16` | managed (RDS / Cloud SQL / Neon) **veya** sertlestirilmis self-host |
| Silme riski | `down -v` | volume/snapshot korumasi, `down -v` asla; managed'da delete-protection |

---

## 1) Instance secimi

### A) Managed (onerilen: RDS / Cloud SQL / Neon / Supabase)
- Superuser'i saglayici verir (`postgres` ya da kisitli bir master user).
- `docker-compose.yml`'deki `POSTGRES_*` env'i **kullanilmaz**; instance'i
  saglayicidan kurarsin.
- `docker/db-provision.*` scripti yine gecerli: master user'i "admin" olarak ver.
- Delete protection + otomatik yedek + minor-version upgrade ac.

### B) Self-host (Docker/VM)
`docker-compose.yml`'i temel al ama:
- `ports:` -> `"127.0.0.1:5432:5432"` (ya da hic yayinlama, sadece internal network).
- `POSTGRES_PASSWORD` -> secret (env_file / docker secret), repoya girmez.
- `POSTGRES_HOST_AUTH_METHOD` **asla** `trust` degil (varsayilan `scram-sha-256`, oyle kalsin).
- Named volume yerine bilinen bir path + disk snapshot politikasi.
- `shared_buffers`, `max_connections`, `work_mem` gibi ayarlari makinenin
  boyutuna gore ver (ya da PgBouncer koy).

---

## 2) Rol modeli (prod'da siki)

Lokalde `pathexplorer` hem admin hem proje. Prod'da ayir:

```sql
-- 1) Provisioning icin ayri superuser
CREATE ROLE dbadmin LOGIN SUPERUSER PASSWORD '<uzun-rastgele>';

-- 2) Her app rolu: superuser DEGIL, sadece kendi db'si
--    (bunu script yapar; elle de ayni)
CREATE ROLE pathexplorer LOGIN PASSWORD '<uzun-rastgele>'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE DATABASE pathexplorer OWNER pathexplorer;
REVOKE CONNECT ON DATABASE pathexplorer FROM PUBLIC;
GRANT  CONNECT ON DATABASE pathexplorer TO pathexplorer;
```

Opsiyonel ekstra sertlestirme (bilgi sizinti yuzeyi):
```sql
REVOKE CONNECT ON DATABASE postgres  FROM PUBLIC;   -- pgAdmin maintenance db'yi degistir
REVOKE CONNECT ON DATABASE template1 FROM PUBLIC;
```

Ileri seviye (istege bagli): her db'de **iki** rol —
- `<proje>_owner` : DDL / migration calistirir (sema sahibi)
- `<proje>_app`   : sadece `SELECT/INSERT/UPDATE/DELETE` (runtime)

```sql
GRANT USAGE ON SCHEMA public TO pathexplorer_app;
ALTER DEFAULT PRIVILEGES FOR ROLE pathexplorer_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO pathexplorer_app;
```

---

## 3) Provision scriptini prod'a yoneltme

Script `-PgHost` verilince `docker exec` yerine dogrudan `psql` kullanir.
Once `psql` client'i ve admin parolasini ayarla:

**PowerShell:**
```powershell
$env:PGADMIN_PASSWORD = (Get-Secret prod-dbadmin)     # secret manager'dan
.\docker\db-provision.ps1 avukat (New-Guid).Guid `
    -PgHost db.prod.internal -PgPort 5432 -AdminUser dbadmin
Remove-Item Env:\PGADMIN_PASSWORD
```

**bash:**
```bash
export PGHOST=db.prod.internal PGPORT=5432
export PGADMIN_USER=dbadmin PGADMIN_PASSWORD="$(vault kv get -field=pw secret/db/dbadmin)"
./docker/db-provision.sh avukat "$(openssl rand -hex 16)"
unset PGADMIN_PASSWORD
```

Cikan parolayi **loga birakma** — dogrudan secret manager'a / uygulamanin
env'ine yaz.

---

## 4) Uygulama baglanti string'i (prod farki)

Lokal:
```
postgresql://avukat:avukat@localhost:5443/avukat
```
Prod:
```
postgresql://avukat:<secret>@db.prod.internal:5432/avukat?sslmode=verify-full&sslrootcert=/etc/ssl/certs/rds-ca.pem
```
JDBC:
```
jdbc:postgresql://db.prod.internal:5432/avukat?ssl=true&sslmode=verify-full
```

- Parola: env / secret manager, repoya/committe **girmez**.
- `db.properties` gibi dosyalarda parola tutma; bu proje zaten
  `PATHEXPLORER_DB_URL/USER/PASSWORD` env'i ile ezilebiliyor — prod'da onu kullan.
- Connection pool: PgBouncer veya uygulama pool'u (`max_connections` sinirini as-ma).

---

## 5) Sema / migration

- `docker/initdb/*.sql` SADECE bos volume'da, ilk baslatmada calisir — prod'da
  buna guvenme.
- Prod'da sema = versiyonlu migration: Flyway / Liquibase / alembic / node-pg-migrate.
- Ilk kurulumda mevcut `initdb/*.sql`'i migration'in V1'i yap.
- Migration'i `<proje>_owner` (ya da script'in actigi rol) ile calistir, runtime
  rolu ile degil.

---

## 6) Yedek & kurtarma

Izole db modeli burada kazandirir: her projeyi **ayri** yedekler/geri yuklersin.

```bash
# gunluk mantiksal yedek (proje basina)
pg_dump -h db.prod.internal -U dbadmin -Fc -d avukat -f avukat_$(date +%F).dump

# geri yukleme
pg_restore -h db.prod.internal -U dbadmin -d avukat --clean --if-exists avukat_2026-01-01.dump
```

- Managed: otomatik snapshot + PITR ac; retention >= 7 gun; restore'u **test et**.
- Self-host: `pg_dump` cron + WAL arsivleme (`archive_mode=on`) veya `pgBackRest`.
- Yedekleri instance'tan **ayri** yerde tut (baska bucket/hesap).

---

## 7) Guvenlik / isletme kontrol listesi

- [ ] Port disariya kapali (VPC / security group / `127.0.0.1` / firewall).
- [ ] `pg_hba.conf`: `trust` yok; `hostssl ... scram-sha-256`.
- [ ] Her rol NOSUPERUSER; ayri `dbadmin` sadece provisioning icin, normalde kapali.
- [ ] Her db'de `CONNECT` PUBLIC'ten alinmis, sadece kendi rolune verilmis.
- [ ] Parolalar >= 24 karakter rastgele, secret manager'da, rotasyon plani var.
- [ ] TLS zorunlu (`sslmode=require`+, tercihen `verify-full`).
- [ ] Otomatik yedek + PITR + **denenmis** restore.
- [ ] `log_connections/log_disconnections`, yavas sorgu logu, log retention.
- [ ] Monitoring (disk %, connection %, replication lag, `pg_stat_statements`).
- [ ] Minor version upgrade politikasi.
- [ ] `docker compose down -v` / `DROP DATABASE` insan hatasina karsi korumali
      (delete protection / ayri onay).
- [ ] `.env`, dump dosyalari, parola iceren her sey `.gitignore`'da.

---

## 8) Yeni proje ekleme akisi (prod)

1. `dbadmin` parolasini secret manager'dan al, env'e koy.
2. `db-provision.*` scriptini `-PgHost/-PgPort -AdminUser dbadmin` ile calistir,
   parola olarak rastgele uret.
3. Cikan parolayi secret manager'a + uygulamanin deploy env'ine yaz (loga degil).
4. Migration aracini yeni db'ye kosla (V1 = initial schema).
5. `sslmode` + prod host ile baglanti string'ini uygulamaya ver.
6. Yedek job'una yeni db adini ekle.
7. `PGADMIN_PASSWORD` env'ini temizle.
