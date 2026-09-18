#!/usr/bin/env bash
# solving_checkpoint'te kaydi olan HER grid boyutu icin ayri bir
# <row>-<col>-checkpoint-extract.txt dosyasi uretir (bu script'in
# bulundugu klasore, yani bash/ altina). Dosya zaten varsa ustune yazilir
# (silinip yeniden olusturulur). Her dosyada o boyutun TUM checkpoint'leri,
# her biri decode edilmis grid halinde, solution_index sirasiyla listelenir.
#
# TUM formatlama (grid satirlari + basliklar) TEK bir SQL sorgusunda
# string_agg ile PostgreSQL tarafinda yapilir; bash sadece TEK parca
# metni dosyaya yazar - satir satir bash donguleri YOK (binlerce
# checkpoint'te bu yaklasim saniyeler ile dakikalar arasindaki farki yaratir).
set -euo pipefail

CONTAINER="${PG_CONTAINER:-dev-postgres}"
PG_USER="${PG_USER:-pathexplorer}"
PG_DB="${PG_DB:-pathexplorer}"
OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SIZES=$(docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -F'|' -c "
SELECT DISTINCT gm.row_size, gm.col_size
FROM solving_checkpoint sc JOIN grid_map gm ON gm.id = sc.grid_map_id
ORDER BY 1,2;")

if [ -z "$SIZES" ]; then
  echo "[extract] solving_checkpoint'te hic kayit yok, yazilacak dosya yok."
  exit 0
fi

while IFS='|' read -r ROW COL; do
  [ -z "$ROW" ] && continue
  OUT_FILE="${OUT_DIR}/${ROW}-${COL}-checkpoint-extract.txt"

  QUERY="
  WITH target AS (
    SELECT sc.solution_index, sc.path, sc.path_len, gm.col_size
    FROM solving_checkpoint sc JOIN grid_map gm ON gm.id = sc.grid_map_id
    WHERE gm.row_size = ${ROW} AND gm.col_size = ${COL}
  ),
  cells AS (
    SELECT solution_index, get_byte(path,k)/col_size AS x, get_byte(path,k)%col_size AS y, k+1 AS step
    FROM target, generate_series(0, path_len-1) AS k
  ),
  rows_per_solution AS (
    SELECT solution_index, y,
      string_agg(CASE WHEN step<10 THEN '| '||step||' |' ELSE '|'||step||' |' END, '' ORDER BY x) AS row_text
    FROM cells GROUP BY solution_index, y
  ),
  blocks AS (
    SELECT solution_index,
      'solution_index = ' || solution_index || E'\n' ||
      string_agg(row_text, E'\n' ORDER BY y DESC) AS block_text
    FROM rows_per_solution
    GROUP BY solution_index
  )
  SELECT count(*), coalesce(string_agg(block_text, E'\n\n' ORDER BY solution_index), '')
  FROM blocks;"

  RESULT=$(docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -F$'\t' -c "$QUERY")
  COUNT="${RESULT%%$'\t'*}"
  BODY="${RESULT#*$'\t'}"

  {
    echo "${ROW}x${COL} - solving_checkpoint tam dokumu"
    echo "Olusturulma: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "========================================"
    echo ""
    echo "$BODY"
    echo ""
    echo "---- Toplam ${COUNT} checkpoint ----"
  } > "$OUT_FILE"

  echo "[extract] ${ROW}x${COL}: ${COUNT} checkpoint -> ${OUT_FILE}"
done <<< "$SIZES"

echo "[extract] Bitti."
