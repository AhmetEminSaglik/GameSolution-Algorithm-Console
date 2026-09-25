#!/usr/bin/env bash
# En guncel checkpoint'i (solving_checkpoint, secilen grid'de, tam dolu path)
# DB'den cekip oyun tahtasi gibi |NN| formatinda cizer. Kullanim:
#   bash bash/latest-grid.sh          -> GRID ortam degiskeni (menu.bat verir), yoksa 7x7
#   bash bash/latest-grid.sh 6x6      -> 6x6
#   bash bash/latest-grid.sh 5        -> 5x5
set -euo pipefail

SIZE_IN="$(echo "${1:-${GRID:-7x7}}" | tr 'X' 'x' | tr -d ' \r')"
if [[ "$SIZE_IN" == *x* ]]; then
  ROW_SIZE="${SIZE_IN%%x*}"
  COL_SIZE="${SIZE_IN##*x}"
else
  ROW_SIZE="$SIZE_IN"
  COL_SIZE="$SIZE_IN"
fi
PATH_LEN=$((ROW_SIZE * COL_SIZE))
CONTAINER="${PG_CONTAINER:-dev-postgres}"
PG_USER="${PG_USER:-pathexplorer}"
PG_DB="${PG_DB:-pathexplorer}"

FILTER="gm.row_size = ${ROW_SIZE} AND gm.col_size = ${COL_SIZE} AND sc.path_len = ${PATH_LEN}"

QUERY="
WITH latest AS (
  SELECT sc.solution_index, sc.path, sc.path_len, gm.col_size, gm.row_size
  FROM solving_checkpoint sc JOIN grid_map gm ON gm.id = sc.grid_map_id
  WHERE ${FILTER}
  ORDER BY sc.solution_index DESC
  LIMIT 1
),
cells AS (
  SELECT solution_index, get_byte(path,k)/col_size AS x, get_byte(path,k)%col_size AS y, k+1 AS step
  FROM latest, generate_series(0, path_len-1) AS k
)
SELECT y,
  string_agg(CASE WHEN step<10 THEN '| '||step||' |' ELSE '|'||step||' |' END, '' ORDER BY x) AS row_text
FROM cells GROUP BY y ORDER BY y DESC;
"

SOLUTION_INDEX=$(docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -c "
SELECT sc.solution_index
FROM solving_checkpoint sc JOIN grid_map gm ON gm.id = sc.grid_map_id
WHERE ${FILTER}
ORDER BY sc.solution_index DESC LIMIT 1;")

if [ -z "$SOLUTION_INDEX" ]; then
  echo "[latest-grid] ${ROW_SIZE}x${COL_SIZE} icin solving_checkpoint'te kayit yok."
  exit 1
fi

echo "En guncel cozum: solution_index = ${SOLUTION_INDEX}  (${ROW_SIZE}x${COL_SIZE})"
echo ""
docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -F'|' -c "$QUERY" | cut -d'|' -f2-
