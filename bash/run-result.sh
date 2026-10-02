#!/usr/bin/env bash
# Sonuc istatistigi: run_result / run_result_square tablolarini hesaplatir
# (refresh_run_result) ve okunur halde basar; ayni ciktiyi
# rapor/RunResult/run-result.txt'ye yazar (makale icin).
#   bash bash/run-result.sh          -> tum grid'ler
#   bash bash/run-result.sh 7x7      -> sadece 7x7 (menu.bat GRID'i verir)
# COMPLETED satirlarda sadece bos alanlar dolar; elle girilen degerler ezilmez.
set -euo pipefail

CONTAINER="${PG_CONTAINER:-dev-postgres}"
PG_USER="${PG_USER:-pathexplorer}"
PG_DB="${PG_DB:-pathexplorer}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT_DIR}/rapor/RunResult"
OUT_FILE="${OUT_DIR}/run-result.txt"

SIZE_IN="$(echo "${1:-${GRID:-}}" | tr 'X' 'x' | tr -d ' \r')"
GRID_FILTER="true"
GRID_ARG="NULL"
if [ -n "$SIZE_IN" ]; then
  if [[ "$SIZE_IN" == *x* ]]; then R="${SIZE_IN%%x*}"; C="${SIZE_IN##*x}"; else R="$SIZE_IN"; C="$SIZE_IN"; fi
  GRID_FILTER="g.row_size = ${R} AND g.col_size = ${C}"
  GRID_ARG="(SELECT id FROM grid_map WHERE row_size = ${R} AND col_size = ${C})"
fi

mkdir -p "$OUT_DIR"

{
  echo "Sonuc istatistigi - $(date '+%Y-%m-%d %H:%M')  (grid: ${SIZE_IN:-hepsi})"
  echo
  docker exec -i "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -q -v ON_ERROR_STOP=1 <<SQL
SET client_min_messages = warning;
SELECT refresh_run_result(${GRID_ARG}::smallint) AS "guncellenen hesaplama";

\echo '=== Hesaplamalar ==='
\x on
SELECT r.id, g.row_size || 'x' || g.col_size AS grid, a.code AS algoritma,
       r.checkpoint_version AS surum, t.code AS tarama, r.save_mode AS kayit, r.status,
       concat_ws(' / ', ct.name, of.name, m.cpu_model, m.ram_gb || ' GB ' || m.ram_type) AS bilgisayar,
       to_char(r.visited_solutions, 'FM999G999G999G999G999') AS gezilen_cozum,
       to_char(r.total_solutions, 'FM999G999G999G999G999') AS toplam_cozum,
       to_char(r.total_steps, 'FM999G999G999G999G999') AS toplam_adim,
       to_char(r.started_at AT TIME ZONE 'Europe/Istanbul', 'YYYY-MM-DD HH24:MI') AS baslangic,
       to_char(r.finished_at AT TIME ZONE 'Europe/Istanbul', 'YYYY-MM-DD HH24:MI') AS bitis,
       r.wall_time AS duvar_saati, r.solve_time AS cozme_suresi, r.idle_time AS bosluk,
       r.restart_count AS yeniden_baslatma, r.unmeasured_rows AS olculmeyen_satir,
       r.solutions_per_sec AS cozum_sn, r.steps_per_sec AS adim_sn,
       r.elapsed_min AS en_kisa_aralik, r.elapsed_max AS en_uzun_aralik,
       r.notes AS notlar
FROM run_result r
JOIN grid_map g ON g.id = r.grid_map_id
JOIN solving_algorithm a ON a.id = r.algorithm_id
JOIN run_map_type t ON t.id = r.run_map_type_id
LEFT JOIN machine m ON m.id = r.machine_id
LEFT JOIN chassis_type ct ON ct.id = m.chassis_type_id
LEFT JOIN os_family of ON of.id = m.os_family_id
WHERE ${GRID_FILTER}
ORDER BY g.row_size, g.col_size, r.save_mode, r.checkpoint_version;
\x off

\echo '=== Kare kare (temel bolge) ==='
SELECT r.id, g.row_size || 'x' || g.col_size AS grid, r.checkpoint_version AS surum, r.save_mode AS kayit,
       '(' || s.x || ',' || s.y || ')' AS kare, 'x' || s.multiplier AS carpan,
       to_char(s.solutions, 'FM999G999G999G999') AS cozum,
       to_char(s.contribution, 'FM999G999G999G999') AS katki,
       s.solve_time AS sure
FROM run_result_square s
JOIN run_result r ON r.id = s.run_result_id
JOIN grid_map g ON g.id = r.grid_map_id
WHERE ${GRID_FILTER}
ORDER BY r.id, s.x, s.y;

\echo '=== Bilgisayarlar ==='
\x on
SELECT * FROM machine ORDER BY id;
\x off
SQL
} | tee "$OUT_FILE"

echo
echo "[run-result] yazildi -> ${OUT_FILE}"
