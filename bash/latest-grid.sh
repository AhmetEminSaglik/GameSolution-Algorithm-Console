#!/usr/bin/env bash
# En guncel checkpoint'i (solving_checkpoint, path_len'e gore) DB'den cekip
# oyun tahtasi gibi |NN| formatinda cizer. Kullanim:
#   bash bash/latest-grid.sh          -> varsayilan 7x7 (path_len=49)
#   bash bash/latest-grid.sh 36       -> 6x6 (path_len=36)
#   bash bash/latest-grid.sh 25       -> 5x5 (path_len=25)
set -euo pipefail

PATH_LEN="${1:-49}"
CONTAINER="${PG_CONTAINER:-dev-postgres}"
PG_USER="${PG_USER:-pathexplorer}"
PG_DB="${PG_DB:-pathexplorer}"

QUERY="
WITH latest AS (
  SELECT sc.solution_index, sc.path, sc.path_len, gm.col_size, gm.row_size
  FROM solving_checkpoint sc JOIN grid_map gm ON gm.id = sc.grid_map_id
  WHERE sc.path_len = ${PATH_LEN}
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

INFO=$(docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -c "
SELECT sc.solution_index, gm.row_size, gm.col_size
FROM solving_checkpoint sc JOIN grid_map gm ON gm.id = sc.grid_map_id
WHERE sc.path_len = ${PATH_LEN}
ORDER BY sc.solution_index DESC LIMIT 1;")

if [ -z "$INFO" ]; then
  echo "[latest-grid] path_len=${PATH_LEN} icin solving_checkpoint'te kayit yok."
  exit 1
fi

SOLUTION_INDEX=$(echo "$INFO" | cut -d'|' -f1)
ROW_SIZE=$(echo "$INFO" | cut -d'|' -f2)
COL_SIZE=$(echo "$INFO" | cut -d'|' -f3)

echo "En guncel cozum: solution_index = ${SOLUTION_INDEX}  (${ROW_SIZE}x${COL_SIZE})"
echo ""
docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -F'|' -c "$QUERY" | cut -d'|' -f2-
