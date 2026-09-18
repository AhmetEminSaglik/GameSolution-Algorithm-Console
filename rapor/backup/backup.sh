#!/usr/bin/env bash
# pathexplorer DB'sinin tam yedegini 3 formatta uretir (bu klasorun altina):
#   pgdump/<timestamp>.dump          -> tam, sikistirilmis, guvenilir geri yukleme
#   csv/<tablo>.csv                  -> Excel/analiz icin
#   sql-insert/<tablo>_insert.txt    -> calistirilabilir INSERT kodu (kucuk tablolar)
#
# csv/ ve sql-insert/ dosyalari HER CALISTIRMADA USTUNE YAZILIR (sabit isim).
# pgdump/ dosyasi HER CALISTIRMADA YENI, zaman damgali bir dosya olarak eklenir
# (gecmis yedekler silinmez - eskilerini elle temizlemen gerekebilir).
#
# solving_checkpoint (7x7 cozucusu) ARKA PLANDA BUYUYEN bir tablo olabilir.
# Ucu de (pgdump/csv/sql-insert) AYNI ANI yansitsin diye: once pgdump alinir,
# pgdump'in solving_checkpoint icin yakaladigi son solution_index bulunur,
# csv + sql-insert bu deger <= filtresiyle SABITLENIR. Boylece uc format da
# birbirini tutar (bkz. onceki backup'ta yasanan 3654/3656/3659 satir sapmasi).
set -euo pipefail

CONTAINER="${PG_CONTAINER:-dev-postgres}"
PG_USER="${PG_USER:-pathexplorer}"
PG_DB="${PG_DB:-pathexplorer}"
OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# INSERT metnine cevirmenin pratik olmadigi satir sinirini asan tablolar
# icin sadece pgdump/csv uretilir (bkz. path_explorer_solution_6x6, ~1GB olurdu).
INSERT_ROW_LIMIT=100000

mkdir -p "$OUT_DIR/pgdump" "$OUT_DIR/csv" "$OUT_DIR/sql-insert"

psql_c() {
  docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -c "$1"
}

TS=$(date '+%Y%m%d_%H%M%S')

echo "[backup] 1/3 pgdump aliniyor..."
DUMP_FILE="$OUT_DIR/pgdump/pathexplorer_${TS}.dump"
docker exec "$CONTAINER" pg_dump -U "$PG_USER" -Fc -d "$PG_DB" > "$DUMP_FILE"
echo "[backup] pgdump tamam -> $DUMP_FILE"

# solving_checkpoint pin noktasi: pgdump'in yakaladigi son solution_index.
CUTOFF=0
if psql_c "SELECT 1 FROM information_schema.tables WHERE table_name='solving_checkpoint';" | grep -q 1; then
  docker cp "$DUMP_FILE" "$CONTAINER:/tmp/backup_pin_check.dump" > /dev/null
  CUTOFF=$(docker exec "$CONTAINER" bash -c \
    "pg_restore -a --table=solving_checkpoint -f - /tmp/backup_pin_check.dump 2>/dev/null | awk -F'\t' 'NF>10{print \$3}' | sort -n | tail -1")
  docker exec "$CONTAINER" rm -f /tmp/backup_pin_check.dump > /dev/null
  [ -z "$CUTOFF" ] && CUTOFF=0
  echo "[backup] solving_checkpoint pin noktasi: solution_index <= ${CUTOFF}"
fi

TABLES=$(psql_c "SELECT relname FROM pg_stat_user_tables WHERE n_live_tup > 0 ORDER BY relname;")

echo "[backup] 2/3 CSV'ler uretiliyor..."
while read -r T; do
  [ -z "$T" ] && continue
  if [ "$T" = "solving_checkpoint" ]; then
    docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c \
      "COPY (SELECT * FROM solving_checkpoint WHERE solution_index <= ${CUTOFF}) TO STDOUT WITH CSV HEADER" \
      > "$OUT_DIR/csv/${T}.csv"
  else
    docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c \
      "COPY (SELECT * FROM ${T}) TO STDOUT WITH CSV HEADER" \
      > "$OUT_DIR/csv/${T}.csv"
  fi
  echo "  - ${T}.csv"
done <<< "$TABLES"

echo "[backup] 3/3 SQL-insert uretiliyor (${INSERT_ROW_LIMIT} satiri asanlar haric)..."
while read -r T; do
  [ -z "$T" ] && continue
  ROWCOUNT=$(psql_c "SELECT count(*) FROM ${T};")
  if [ "$ROWCOUNT" -gt "$INSERT_ROW_LIMIT" ]; then
    echo "  - ${T}: ${ROWCOUNT} satir, cok buyuk -> atlandi (pgdump/csv kullan)"
    continue
  fi
  if [ "$T" = "solving_checkpoint" ]; then
    PIN_TABLE="solving_checkpoint_pin_${CUTOFF}"
    docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c \
      "CREATE TABLE ${PIN_TABLE} AS SELECT * FROM solving_checkpoint WHERE solution_index <= ${CUTOFF};" > /dev/null
    docker exec "$CONTAINER" pg_dump -U "$PG_USER" -d "$PG_DB" -t "$PIN_TABLE" --data-only --inserts --column-inserts \
      | sed "s/${PIN_TABLE}/solving_checkpoint/g" \
      | grep -vF '\restrict' | grep -vF '\unrestrict' \
      > "$OUT_DIR/sql-insert/${T}_insert.txt"
    docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "DROP TABLE ${PIN_TABLE};" > /dev/null
  else
    docker exec "$CONTAINER" pg_dump -U "$PG_USER" -d "$PG_DB" -t "${T}" --data-only --inserts --column-inserts \
      | grep -vF '\restrict' | grep -vF '\unrestrict' \
      > "$OUT_DIR/sql-insert/${T}_insert.txt"
  fi
  echo "  - ${T}_insert.txt"
done <<< "$TABLES"

echo "[backup] Bitti -> ${OUT_DIR}"
