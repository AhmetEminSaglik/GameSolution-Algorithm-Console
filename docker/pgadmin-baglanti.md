# Paylaşımlı lokal Postgres — bağlantı rehberi

`docker-compose.yml` içindeki `dev-postgres` container'ına nasıl bağlanılır.
Bütün yan projeler bu **tek** instance'ı paylaşır; her proje ayrı bir **database**
(ayrı port değil).

## Bağlantı bilgisi

| Alan | Değer |
|---|---|
| Host | `localhost` |
| Port | `5443` |
| Database | `pathexplorer` |
| Username | `pathexplorer` |
| Password | `pathexplorer` |
| JDBC URL | `jdbc:postgresql://localhost:5443/pathexplorer?reWriteBatchedInserts=true` |

> Container'ı içeriden çalıştırırken port `5432`, container adı `dev-postgres`.
> Dışarıdan (host / pgAdmin / DBeaver) port `5443`.

## Önce container ayakta mı?

```bash
docker ps --filter name=dev-postgres
docker exec dev-postgres psql -U pathexplorer -d pathexplorer -c "\dt"
```

Tablolar listeleniyorsa hazır. Değilse:

```bash
cd C:\btk\dev\GameSolution-Algorithm-Console
docker compose up -d
```

## pgAdmin 4'te sunucu kaydetme

1. Sol ağaçta **Servers**'a **sağ tık** → **Register** → **Server…**
   (bazı sürümlerde **Create** → **Server…**)
2. **General** sekmesi:
   - **Name:** `local-dev`  *(sadece etiket, istediğini yaz)*
3. **Connection** sekmesi:
   | Alan | Değer |
   |---|---|
   | Host name/address | `localhost` |
   | Port | `5443` |
   | Maintenance database | `pathexplorer` |
   | Username | `pathexplorer` |
   | Password | `pathexplorer` |
   | Save password? | ✓ |
4. **Save**

### Tabloları bulma

```
local-dev
└── Databases
    └── pathexplorer
        └── Schemas
            └── public
                └── Tables
                    ├── solver_run
                    ├── path_explorer_solution (+ _5x5, _6x6, … partition)
                    ├── grid_map
                    └── solution_step (+ _m1 … _m6 partition)
```

Görünmüyorsa **Tables** → sağ tık → **Refresh**.

## DBeaver / IntelliJ Database / başka GUI

Yeni bağlantı → **PostgreSQL** → aynı değerler:

- Host `localhost`, Port `5443`
- Database `pathexplorer`
- User `pathexplorer`, Password `pathexplorer`

## psql (terminalden)

```bash
# host üzerinden (psql kuruluysa)
psql "host=localhost port=5443 dbname=pathexplorer user=pathexplorer password=pathexplorer"

# ya da container içinden
docker exec -it dev-postgres psql -U pathexplorer -d pathexplorer
```

## Uygulama (bu proje) bağlantısı

`db.properties` (kök) zaten bu değerlerle geliyor. Değiştirmek yerine ortam
değişkeni ezebilir:

```
PATHEXPLORER_DB_URL=jdbc:postgresql://localhost:5443/pathexplorer?reWriteBatchedInserts=true
PATHEXPLORER_DB_USER=pathexplorer
PATHEXPLORER_DB_PASSWORD=pathexplorer
```

## İleride yeni proje eklerken

Aynı instance'a **izole** yeni rol + database aç (yeni port yok). Tek komut:

```powershell
.\docker\db-provision.ps1 yeniproje s3cret
```
```bash
./docker/db-provision.sh yeniproje s3cret
```

Her proje sadece kendi database'ine bağlanır. pgAdmin'de bu yeni database'i
görmek için onu kendi rolüyle ayrı bir sunucu olarak kaydet (Host `localhost`,
Port `5443`, Maintenance DB = `yeniproje`, Username/Password = `yeniproje` rolü) —
`local-dev` (pathexplorer) altında görünmez, çünkü `pathexplorer` rolünün o
database'e erişimi yoktur.

Detay: kökte **`postgre-connection.md`** (lokal) ve **`postgre-prod.md`** (prod).

## Veri güvenliği

| Komut | Sonuç |
|---|---|
| `docker compose down` | Durur, **veri kalır** — bunu kullan |
| `docker compose down -v` | **Volume silinir, tüm veri gider** — kullanma |
