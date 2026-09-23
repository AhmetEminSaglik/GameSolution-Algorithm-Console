#!/usr/bin/env bash
# pathexplorer DB'sinin tam yedegini 3 formatta uretir (bu klasorun altina):
#   pgdump/<timestamp>.dump          -> tam, sikistirilmis, guvenilir geri yukleme
#   csv/<tablo>.csv                  -> Excel/analiz icin
#   csv/<tablo>_<R>x<C>.csv          -> grid_map_id kolonu olan tablolar
#                                       (solving_checkpoint, solver_run, ...)
#                                       grid boyutuna gore AYRI dosyalara bolunur
#                                       (orn. solving_checkpoint_6x6.csv, _7x7.csv)
#   sql-insert/<tablo>_insert.txt    -> calistirilabilir INSERT kodu (kucuk tablolar)
#
# csv/ klasorundeki eski .csv'ler HER CALISTIRMADA SILINIP yeniden uretilir
# (grid bazli isimler degisebildigi icin; eski dosya kalip import'ta cift
# yuklenmesin). sql-insert/ dosyalari ustune yazilir (sabit isim).
# pgdump/ dosyasi HER CALISTIRMADA YENI, zaman damgali bir dosya olarak eklenir
# (gecmis yedekler silinmez - eskilerini elle temizlemen gerekebilir).
#
# solving_checkpoint (6x6/7x7 cozuculeri) ARKA PLANDA BUYUYEN bir tablo olabilir.
# Ucu de (pgdump/csv/sql-insert) AYNI ANI yansitsin diye: once pgdump alinir,
# pgdump'in solving_checkpoint icin yakaladigi son solution_index HER
# solving_run_id icin ayri bulunur (farkli run'larin solution_index'leri
# birbiriyle kiyaslanamaz), csv + sql-insert bu (run, <= index) filtresiyle
# SABITLENIR. Boylece uc format da birbirini tutar (bkz. onceki backup'ta
# yasanan 3654/3656/3659 satir sapmasi).
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

# solving_checkpoint pin noktasi: pgdump'in yakaladigi son solution_index,
# her solving_run_id icin ayri. PIN_JOIN, "solving_checkpoint t" arkasina
# eklenen JOIN ifadesi (pin yoksa hic satir secmez).
PIN_JOIN="JOIN (VALUES (NULL::uuid, 0::bigint)) pin(run_id, max_idx) ON false"
if psql_c "SELECT 1 FROM information_schema.tables WHERE table_name='solving_checkpoint';" | grep -q 1; then
  docker cp "$DUMP_FILE" "$CONTAINER:/tmp/backup_pin_check.dump" > /dev/null
  # bash -c icinde: Git Bash /tmp/... argumanini Windows yoluna cevirmesin.
  PIN_VALUES=$(docker exec "$CONTAINER" bash -c "pg_restore -a --table=solving_checkpoint -f - /tmp/backup_pin_check.dump 2>/dev/null" \
    | awk -F'\t' 'NF>10 { if (!($2 in m) || $3+0 > m[$2]) m[$2] = $3+0 }
                  END   { for (r in m) printf "%s(%c%s%c::uuid, %d)", (n++ ? ", " : ""), 39, r, 39, m[r] }')
  docker exec "$CONTAINER" rm -f /tmp/backup_pin_check.dump > /dev/null
  if [ -n "$PIN_VALUES" ]; then
    PIN_JOIN="JOIN (VALUES ${PIN_VALUES}) pin(run_id, max_idx) ON t.solving_run_id = pin.run_id AND t.solution_index <= pin.max_idx"
  fi
  echo "[backup] solving_checkpoint pin noktasi (run -> solution_index <=): ${PIN_VALUES:-yok}"
fi

# Tablo icin satir secen FROM ifadesi (solving_checkpoint ise pin'li).
table_from() {
  if [ "$1" = "solving_checkpoint" ]; then
    echo "solving_checkpoint t ${PIN_JOIN}"
  else
    echo "$1 t"
  fi
}

# Sadece gercek (leaf) tablolar: partition'li parent'lar (path_explorer_solution,
# solution_step) atlanir, verileri zaten partition'larinda - yoksa cift yazilir.
# Bosluk kontrolu EXISTS ile: pg_stat n_live_tup restore sonrasi ANALYZE
# olmadan 0 kalabiliyor (grid_map vb. yanlislikla atlaniyordu).
ALL_TABLES=$(psql_c "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relkind = 'r' ORDER BY 1;")
TABLES=""
while read -r T; do
  [ -z "$T" ] && continue
  if [ "$(psql_c "SELECT EXISTS (SELECT 1 FROM ${T});")" = "t" ]; then
    TABLES+="${T}"$'\n'
  fi
done <<< "$ALL_TABLES"

echo "[backup] 2/3 CSV'ler uretiliyor (grid_map_id olanlar grid bazli ayrilir)..."
rm -f "$OUT_DIR/csv/"*.csv
while read -r T; do
  [ -z "$T" ] && continue
  FROM=$(table_from "$T")
  HAS_GRID=$(psql_c "SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='${T}' AND column_name='grid_map_id';")
  if [ -n "$HAS_GRID" ]; then
    # Her grid icin ayri dosya: <tablo>_<row>x<col>.csv
    GRIDS=$(psql_c "SELECT g.id || ' ' || g.row_size || 'x' || g.col_size FROM grid_map g WHERE EXISTS (SELECT 1 FROM ${T} x WHERE x.grid_map_id = g.id) ORDER BY g.id;")
    while read -r GID GSIZE; do
      [ -z "$GID" ] && continue
      docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c \
        "COPY (SELECT t.* FROM ${FROM} WHERE t.grid_map_id = ${GID}) TO STDOUT WITH CSV HEADER" \
        > "$OUT_DIR/csv/${T}_${GSIZE}.csv"
      echo "  - ${T}_${GSIZE}.csv"
    done <<< "$GRIDS"
  else
    docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c \
      "COPY (SELECT t.* FROM ${FROM}) TO STDOUT WITH CSV HEADER" \
      > "$OUT_DIR/csv/${T}.csv"
    echo "  - ${T}.csv"
  fi
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
    PIN_TABLE="solving_checkpoint_pin_tmp"
    docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c \
      "DROP TABLE IF EXISTS ${PIN_TABLE}; CREATE TABLE ${PIN_TABLE} AS SELECT t.* FROM $(table_from solving_checkpoint);" > /dev/null
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
