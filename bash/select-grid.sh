#!/usr/bin/env bash
# menu.bat icin grid boyutu secer ve bu klasordeki .selected-grid dosyasina
# yazar (orn. "7x7"). menu.bat bu dosyayi okuyup baslikta gosterir ve
# grid isteyen araclara GRID ortam degiskeni olarak gecirir.
# Bos giris = iptal (mevcut secim degismez).
set -euo pipefail

CONTAINER="${PG_CONTAINER:-dev-postgres}"
PG_USER="${PG_USER:-pathexplorer}"
PG_DB="${PG_DB:-pathexplorer}"
OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEL_FILE="${OUT_DIR}/.selected-grid"

psql_query() {
  docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -F'|' -c "$1"
}

echo "Grid boyutlari:"
psql_query "
  SELECT gm.row_size, gm.col_size, count(sc.grid_map_id)
  FROM grid_map gm LEFT JOIN solving_checkpoint sc ON sc.grid_map_id = gm.id
  GROUP BY 1,2 ORDER BY 1,2;" | while IFS='|' read -r R C N; do
    [ -z "$R" ] && continue
    echo "  ${R}x${C}  (${N} checkpoint)"
  done
echo ""
[ -f "$SEL_FILE" ] && echo "Mevcut secim: $(head -1 "$SEL_FILE")"
read -rp "Grid boyutu (orn. 7 ya da 7x7, bos=iptal): " SIZE_IN
# cmd'den gelen girdide sonda \r kalabiliyor, bosluklarla birlikte temizle.
SIZE_IN=$(echo "$SIZE_IN" | tr 'X' 'x' | tr -d ' \r')
if [ -z "$SIZE_IN" ]; then
  echo "Secim degismedi."
  exit 0
fi
if [[ "$SIZE_IN" == *x* ]]; then
  ROW="${SIZE_IN%%x*}"
  COL="${SIZE_IN##*x}"
else
  ROW="$SIZE_IN"
  COL="$SIZE_IN"
fi

if ! [[ "$ROW" =~ ^[0-9]+$ && "$COL" =~ ^[0-9]+$ ]] \
   || [ -z "$(psql_query "SELECT 1 FROM grid_map WHERE row_size=${ROW} AND col_size=${COL};")" ]; then
  echo "[hata] '${SIZE_IN}' grid_map'te yok, secim degismedi."
  exit 1
fi

echo "${ROW}x${COL}" > "$SEL_FILE"
echo "Secilen grid: ${ROW}x${COL}"
