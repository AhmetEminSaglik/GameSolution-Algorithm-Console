#!/usr/bin/env bash
# Paylasimli Postgres'te bir projeye IZOLE rol + database acar.
#
# Model: her proje = kendi database'i + kendi rolu. Rol SADECE kendi
# database'ine baglanabilir (CONNECT PUBLIC'ten alinir), superuser degildir,
# baska database/rol olusturamaz. Farkli projeler birbirinin verisini goremez.
#
# Kullanim:
#   docker/db-provision.sh <isim> [parola] [--admin-user U] [--admin-db D] [--container C]
#
# Ornek:
#   docker/db-provision.sh avukat                 # parola = "avukat"
#   docker/db-provision.sh avukat s3cret          # parola = "s3cret"
#   PGADMIN_PASSWORD=... docker/db-provision.sh avukat s3cret --admin-user dbadmin
#
# Idempotent: tekrar calistirmak guvenli (var olani bozmaz, eksigi tamamlar).
# Prod icin: --container yok; PGHOST/PGPORT/PGADMIN_* env ver (bkz. postgre-prod.md).

set -euo pipefail

NAME="${1:-}"
if [[ -z "$NAME" || "$NAME" == -* ]]; then
  echo "kullanim: $0 <isim> [parola] [--admin-user U] [--admin-db D] [--container C]" >&2
  exit 1
fi
shift
PASSWORD="${1:-}"
if [[ -n "${PASSWORD}" && "${PASSWORD}" != -* ]]; then shift; else PASSWORD="$NAME"; fi

ADMIN_USER="${PGADMIN_USER:-pathexplorer}"
ADMIN_DB="${PGADMIN_DB:-postgres}"
CONTAINER="${PG_CONTAINER:-dev-postgres}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --admin-user) ADMIN_USER="$2"; shift 2;;
    --admin-db)   ADMIN_DB="$2"; shift 2;;
    --container)  CONTAINER="$2"; shift 2;;
    *) echo "bilinmeyen arg: $1" >&2; exit 1;;
  esac
done

# İsim dogrulama: sadece kucuk harf/rakam/alt cizgi (SQL injection ve tirnak derdi yok).
if [[ ! "$NAME" =~ ^[a-z_][a-z0-9_]*$ ]]; then
  echo "isim sadece [a-z0-9_] olmali (kucuk harfle/altcizgiyle baslar): $NAME" >&2
  exit 1
fi

# psql'i nasil calistiracagiz: local -> docker exec, prod -> dogrudan psql + PGHOST/PGPORT
PGOPTS="-c client_min_messages=warning"
if [[ -n "${PGHOST:-}" ]]; then
  run_admin() { PGPASSWORD="${PGADMIN_PASSWORD:-}" PGOPTIONS="$PGOPTS" psql -v ON_ERROR_STOP=1 -U "$ADMIN_USER" -d "$1" -X -q -t -A; }
  echo ">> hedef: ${PGHOST}:${PGPORT:-5432}  admin=${ADMIN_USER}"
else
  run_admin() { docker exec -e PGOPTIONS="$PGOPTS" -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$ADMIN_USER" -d "$1" -X -q -t -A; }
  echo ">> hedef: docker container ${CONTAINER}  admin=${ADMIN_USER}"
fi

echo ">> rol + database: ${NAME}"

# 1) ROL (idempotent). Var olan rolu superuser'sa DOKUNMA (kendini kilitleme).
run_admin "$ADMIN_DB" <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${NAME}') THEN
    CREATE ROLE ${NAME} LOGIN PASSWORD '${PASSWORD}'
      NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    RAISE NOTICE 'rol olusturuldu: ${NAME}';
  ELSE
    RAISE NOTICE 'rol zaten var: ${NAME} (parola/attribute degistirilmedi)';
  END IF;
END
\$\$;
SQL

# 2) DATABASE (CREATE DATABASE tx disinda olmali; kosullu).
if ! run_admin "$ADMIN_DB" <<SQL | grep -q 1
SELECT 1 FROM pg_database WHERE datname = '${NAME}';
SQL
then
  run_admin "$ADMIN_DB" <<SQL
CREATE DATABASE ${NAME} OWNER ${NAME};
SQL
  echo ">> database olusturuldu: ${NAME}"
else
  echo ">> database zaten var: ${NAME}"
fi

# 3) IZOLASYON: bu database'e sadece kendi rolu baglanabilsin.
run_admin "$ADMIN_DB" <<SQL
REVOKE CONNECT ON DATABASE ${NAME} FROM PUBLIC;
GRANT  CONNECT ON DATABASE ${NAME} TO ${NAME};
ALTER DATABASE ${NAME} OWNER TO ${NAME};
SQL

# 4) public schema: sadece kendi rolu obje olustursun.
run_admin "$NAME" <<SQL
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO ${NAME};
GRANT ALL ON SCHEMA public TO ${NAME};
SQL

PORT="${PGPORT:-5443}"
[[ -n "${PGHOST:-}" ]] || PORT="${PG_PORT:-5443}"
HOST="${PGHOST:-localhost}"

cat <<EOF

==================================================================
  HAZIR:  rol=${NAME}  database=${NAME}
==================================================================
  psql URI : postgresql://${NAME}:${PASSWORD}@${HOST}:${PORT}/${NAME}
  JDBC     : jdbc:postgresql://${HOST}:${PORT}/${NAME}
  .env     : DATABASE_URL=postgresql://${NAME}:${PASSWORD}@${HOST}:${PORT}/${NAME}
             PGHOST=${HOST}
             PGPORT=${PORT}
             PGDATABASE=${NAME}
             PGUSER=${NAME}
             PGPASSWORD=${PASSWORD}

  test     : docker exec -it ${CONTAINER} psql -U ${NAME} -d ${NAME} -c "\\conninfo"
  sema yukle: docker exec -i ${CONTAINER} psql -U ${NAME} -d ${NAME} < yol/schema.sql
==================================================================
EOF
