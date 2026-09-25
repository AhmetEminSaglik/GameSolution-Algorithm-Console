#!/usr/bin/env bash
# solving_checkpoint'ten SIRA NO araligina gore (1-tabanli, solution_index'e
# gore siralanmis liste sirasi - DB'deki solution_index DEGIL, Main.java'daki
# runCheckpointRange'in "N-M" mantigiyla ayni) secilen checkpoint'leri decode
# edip tek bir txt dosyasina yazar: checkpoint-<ROW>x<COL>-<N>-<M>.txt
# GRID ortam degiskeni (orn. GRID=7x7) verilirse grid boyutu sorulmaz.
set -euo pipefail

CONTAINER="${PG_CONTAINER:-dev-postgres}"
PG_USER="${PG_USER:-pathexplorer}"
PG_DB="${PG_DB:-pathexplorer}"
OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

psql_query() {
  docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -F'|' -c "$1"
}

# GRID ortam degiskeni (menu.bat'taki secili grid, orn. 7x7) verilmisse grid
# SORULMAZ, hep o grid kullanilir; verilmemisse her turda sorulur.
PRESET_GRID="$(echo "${GRID:-}" | tr 'X' 'x' | tr -d ' \r')"

while true; do
  if [ -n "$PRESET_GRID" ]; then
    SIZE_IN="$PRESET_GRID"
    echo "Grid: ${SIZE_IN} (degistirmek icin menu 4)"
  else
    echo "Checkpoint'i olan grid boyutlari:"
    psql_query "
      SELECT gm.row_size, gm.col_size, count(*)
      FROM solving_checkpoint sc JOIN grid_map gm ON gm.id = sc.grid_map_id
      GROUP BY 1,2 ORDER BY 1,2;" | while IFS='|' read -r R C N; do
        [ -z "$R" ] && continue
        echo "  ${R}x${C}  (${N} checkpoint)"
      done
    echo ""
    read -rp "Grid boyutu (orn. 7 ya da 7x7): " SIZE_IN
    SIZE_IN=$(echo "$SIZE_IN" | tr 'X' 'x' | tr -d ' ')
  fi
  if [[ "$SIZE_IN" == *x* ]]; then
    ROW="${SIZE_IN%%x*}"
    COL="${SIZE_IN##*x}"
  else
    ROW="$SIZE_IN"
    COL="$SIZE_IN"
  fi

  TOTAL=$(psql_query "
    SELECT count(*) FROM solving_checkpoint sc JOIN grid_map gm ON gm.id = sc.grid_map_id
    WHERE gm.row_size=${ROW} AND gm.col_size=${COL};")

  if [ "${TOTAL:-0}" -eq 0 ]; then
    echo "[hata] ${ROW}x${COL} icin checkpoint yok."
  else
    echo "${ROW}x${COL}: toplam ${TOTAL} checkpoint (sira no 1..${TOTAL})."
    read -rp "Sira araligi (N-M, orn. 10-12): " RANGE_IN
    N="$(echo "${RANGE_IN%%-*}" | tr -d ' ')"
    M="$(echo "${RANGE_IN##*-}" | tr -d ' ')"

    if ! [[ "$N" =~ ^[0-9]+$ ]] || ! [[ "$M" =~ ^[0-9]+$ ]] || [ "$N" -lt 1 ] || [ "$M" -lt "$N" ] || [ "$M" -gt "$TOTAL" ]; then
      echo "[hata] gecersiz aralik: '${RANGE_IN}' (1..${TOTAL} arasinda, N<=M olmali)"
    else
      OUT_FILE="${OUT_DIR}/checkpoint-${ROW}x${COL}-${N}-${M}.txt"

      QUERY="
      WITH numbered AS (
        SELECT sc.solution_index, sc.path, sc.path_len, gm.col_size,
               ROW_NUMBER() OVER (ORDER BY sc.solution_index) AS rn
        FROM solving_checkpoint sc JOIN grid_map gm ON gm.id = sc.grid_map_id
        WHERE gm.row_size=${ROW} AND gm.col_size=${COL}
      ),
      picked AS (SELECT * FROM numbered WHERE rn BETWEEN ${N} AND ${M}),
      cells AS (
        SELECT rn, solution_index, get_byte(path,k)/col_size AS x, get_byte(path,k)%col_size AS y, k+1 AS step
        FROM picked, generate_series(0, path_len-1) AS k
      ),
      rows_per_solution AS (
        SELECT rn, solution_index, y,
          string_agg(CASE WHEN step<10 THEN '| '||step||' |' ELSE '|'||step||' |' END, '' ORDER BY x) AS row_text
        FROM cells GROUP BY rn, solution_index, y
      ),
      blocks AS (
        SELECT rn,
          'sira no = ' || rn || '   solution_index = ' || solution_index || E'\n' ||
          string_agg(row_text, E'\n' ORDER BY y DESC) AS block_text
        FROM rows_per_solution
        GROUP BY rn, solution_index
      )
      SELECT count(*), coalesce(string_agg(block_text, E'\n\n' ORDER BY rn), '')
      FROM blocks;"

      RESULT=$(docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -F$'\t' -c "$QUERY")
      COUNT="${RESULT%%$'\t'*}"
      BODY="${RESULT#*$'\t'}"

      {
        echo "${ROW}x${COL} - sira no ${N}..${M} arasi checkpoint dokumu"
        echo "Olusturulma: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "========================================"
        echo ""
        echo "$BODY"
        echo ""
        echo "---- Toplam ${COUNT} checkpoint (sira ${N}-${M}) ----"
      } > "$OUT_FILE"

      echo "[extract] ${COUNT} checkpoint yazildi -> ${OUT_FILE}"
    fi
  fi

  echo ""
  read -rp "Baska bir aralik icin ENTER, cikmak icin 'exit' yaz: " ans
  if [ "$ans" = "exit" ]; then
    break
  fi
  echo ""
done

echo "Kapatiliyor..."
