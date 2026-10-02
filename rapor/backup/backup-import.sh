#!/usr/bin/env bash
# rapor/backup/{pgdump,csv,sql-insert} klasorlerinden birini secip pathexplorer
# DB'sine GERI YUKLER. docker-compose.yml'daki AYNI container/kullanici/db
# adlarini kullanir (PG_CONTAINER/PG_USER/PG_DB ile override edilebilir).
#
# YIKICI ISLEM: hedef tablolarda ZATEN VERI VARSA, once ONAY ister, sonra
# o tablolari TAMAMEN SILIP (TRUNCATE ... CASCADE) dosyadan yeniden doldurur.
# Boylece eski + yeni veri KARISMAZ. Onay verilmezse HICBIR SEY DEGISMEZ.
#
# Yeni (bos) bir bilgisayarda:
#   1) docker compose up -d   (docker/initdb/*.sql semayi kurar; grid_map,
#      solving_algorithm, checkpoint_version birkac hazir satirla gelir -
#      bu yuzden "zaten veri var" onayi yine sorulur, SIL yaz)
#   2) backup-import.bat -> 1) CSV (onerilen) ya da 3) SQL-insert.
#      pgdump/ git'te YOK; sadece yedegin alindigi makinede vardir.
#   6x6 flat CSV (path_explorer_solution_m2_6x6.csv, ~1.1 GB) git'te yok, o tablo bos kalir.
set -euo pipefail

CONTAINER="${PG_CONTAINER:-dev-postgres}"
PG_USER="${PG_USER:-pathexplorer}"
PG_DB="${PG_DB:-pathexplorer}"
BACKUP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

psql_c() {
  docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A -c "$1"
}

echo "Import kaynagi sec:"
echo "  1) CSV        (${BACKUP_DIR}/csv/)"
echo "  2) pgdump     (${BACKUP_DIR}/pgdump/ - en son .dump dosyasi)"
echo "  3) SQL-insert (${BACKUP_DIR}/sql-insert/)"
echo "  0) Cikis"
read -rp "Secim: " CHOICE

case "$CHOICE" in
  1) SRC="csv" ;;
  2) SRC="pgdump" ;;
  3) SRC="sql-insert" ;;
  0) echo "Cikiliyor."; exit 0 ;;
  *) echo "[hata] gecersiz secim."; exit 1 ;;
esac

# ---- 2) pgdump: tam restore, --clean zaten "once sil sonra olustur" yapar ----
if [ "$SRC" = "pgdump" ]; then
  DUMP_FILE=$(ls -t "${BACKUP_DIR}/pgdump/"*.dump 2>/dev/null | head -1)
  if [ -z "$DUMP_FILE" ]; then
    echo "[hata] pgdump/ klasorunde .dump dosyasi yok."
    exit 1
  fi
  echo "Kullanilacak dump: $(basename "$DUMP_FILE")"

  TOTAL_ROWS=$(psql_c "SELECT coalesce(sum(n_live_tup),0) FROM pg_stat_user_tables;")
  if [ "${TOTAL_ROWS:-0}" -gt 0 ]; then
    echo ""
    echo "!!! DB'de zaten veri var (toplam ${TOTAL_ROWS} satir). !!!"
    echo "pgdump restore --clean ile calisir: MEVCUT TUM TABLOLAR SILINIP"
    echo "dump'taki haliyle YENIDEN OLUSTURULUR. Bu geri alinamaz."
    read -rp "Emin misin? (onaylamak icin SIL yaz): " CONFIRM
    if [ "$CONFIRM" != "SIL" ]; then
      echo "Iptal edildi, hicbir sey degismedi."
      exit 0
    fi
  fi

  docker cp "$DUMP_FILE" "${CONTAINER}:/tmp/restore_import.dump"
  docker exec "$CONTAINER" pg_restore -U "$PG_USER" -d "$PG_DB" --clean --if-exists /tmp/restore_import.dump
  docker exec "$CONTAINER" rm -f /tmp/restore_import.dump
  echo "[import] pgdump geri yuklendi."
  exit 0
fi

# ---- 1) CSV / 3) SQL-insert: klasordeki dosyalardan tablo listesini cikar ----
if [ "$SRC" = "csv" ]; then
  FILES=("${BACKUP_DIR}/csv/"*.csv)
else
  FILES=("${BACKUP_DIR}/sql-insert/"*_insert.txt)
fi

if [ ! -e "${FILES[0]}" ]; then
  echo "[hata] ${SRC}/ klasorunde dosya yok."
  exit 1
fi

# Dosya -> tablo eslemesi. CSV'lerde grid bazli ayrilmis dosyalar
# (<tablo>_<R>x<C>.csv, orn. solving_checkpoint_7x7.csv) ayni tabloya gider;
# ama adi zaten NxM ile biten gercek tablolar (eski path_explorer_solution_6x6 gibi)
# once birebir isimle aranir. TABLE_FILES[tablo] = o tabloya ait dosyalar.
declare -A TABLE_FILES=()
TABLES=()
for f in "${FILES[@]}"; do
  base="$(basename "$f")"
  if [ "$SRC" = "csv" ]; then
    t="${base%.csv}"
  else
    t="${base%_insert.txt}"
  fi
  # sql-insert de CSV gibi grid bazli: <tablo>_<R>x<C>_insert.txt
  if [ -z "$(psql_c "SELECT 1 FROM information_schema.tables WHERE table_name='${t}';")" ] \
     && [[ "$t" =~ ^(.+)_[0-9]+x[0-9]+$ ]]; then
    t="${BASH_REMATCH[1]}"
  fi
  [ -z "${TABLE_FILES[$t]+x}" ] && TABLES+=("$t")
  TABLE_FILES[$t]+="${f}"$'\n'
done

echo ""
echo "Su tablolar import edilecek (${SRC}):"
for t in "${TABLES[@]}"; do
  echo "  - $t  <- $(echo -n "${TABLE_FILES[$t]}" | xargs -d '\n' -n1 basename | paste -sd, -)"
done

echo ""
echo "Mevcut veri kontrolu:"
NEEDS_CONFIRM=0
for t in "${TABLES[@]}"; do
  EXISTS=$(psql_c "SELECT 1 FROM information_schema.tables WHERE table_name='${t}';")
  if [ -z "$EXISTS" ]; then
    echo "  - ${t}: DB'de boyle bir tablo yok, atlanacak"
    continue
  fi
  CNT=$(psql_c "SELECT count(*) FROM ${t};")
  echo "  - ${t}: ${CNT} satir mevcut"
  if [ "${CNT:-0}" -gt 0 ]; then
    NEEDS_CONFIRM=1
  fi
done

if [ "$NEEDS_CONFIRM" -eq 1 ]; then
  echo ""
  echo "!!! Yukaridaki tablolardan bazilarinda ZATEN VERI VAR. !!!"
  echo "Import edilirse bu tablolar ONCE TAMAMEN SILINECEK (TRUNCATE CASCADE),"
  echo "sonra ${SRC} dosyasindan yeniden doldurulacak. Eski veri + yeni veri"
  echo "KARISMASIN diye boyle yapiliyor. Bu islem GERI ALINAMAZ."
  read -rp "Emin misin? (onaylamak icin SIL yaz): " CONFIRM
  if [ "$CONFIRM" != "SIL" ]; then
    echo "Iptal edildi, hicbir sey degismedi."
    exit 0
  fi
fi

# Sadece DB'de gercekten var olan tablolarla devam et.
EXISTING_TABLES=()
for t in "${TABLES[@]}"; do
  EXISTS=$(psql_c "SELECT 1 FROM information_schema.tables WHERE table_name='${t}';")
  [ -n "$EXISTS" ] && EXISTING_TABLES+=("$t")
done

# FK bagimlilik sirasi: ONCE referans verilen (parent) tablolar, SONRA onlara
# bagimli (child) tablolar. Once hepsini TEK bir TRUNCATE ile birlikte
# temizlemek (CASCADE'in ayri ayri, sirasi gelmemis tablolari bosaltip
# yariminda birakma riskini onler), sonra bu sirayla yeniden doldurmak icin.
PARENT_ORDER=(grid_map solving_algorithm checkpoint_version run_map_type os_family chassis_type cpu_vendor machine solver_run run_result)
ORDERED_TABLES=()
for p in "${PARENT_ORDER[@]}"; do
  for t in "${EXISTING_TABLES[@]}"; do
    [ "$t" = "$p" ] && ORDERED_TABLES+=("$t")
  done
done
for t in "${EXISTING_TABLES[@]}"; do
  already=0
  for o in "${ORDERED_TABLES[@]}"; do
    [ "$t" = "$o" ] && already=1
  done
  [ "$already" -eq 0 ] && ORDERED_TABLES+=("$t")
done

echo ""
echo "[import] tum hedef tablolar TEK seferde temizleniyor: ${ORDERED_TABLES[*]}"
JOINED=$(IFS=,; echo "${ORDERED_TABLES[*]}")
docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c "TRUNCATE TABLE ${JOINED} CASCADE;" > /dev/null

for t in "${ORDERED_TABLES[@]}"; do
  if [ "$SRC" = "csv" ]; then
    while read -r f; do
      [ -z "$f" ] && continue
      echo "[import] ${t}: $(basename "$f") yukleniyor..."
      # Kolon listesi CSV basligindan: generated kolon (checkpoint_no) olmayan
      # yeni yedekler, tablo kolon sirasindan bagimsiz dogru eslesir.
      HEADER_COLS=$(head -1 "$f" | tr -d '\r')
      docker exec -i "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c \
        "COPY ${t} (${HEADER_COLS}) FROM STDIN WITH CSV HEADER" < "$f"
    done <<< "${TABLE_FILES[$t]}"
  else
    while read -r f; do
      [ -z "$f" ] && continue
      echo "[import] ${t}: $(basename "$f") yukleniyor..."
      # IDENTITY ALWAYS kolonlara (solver_run.id, path_explorer_solution.id)
      # acik deger yazabilmek icin OVERRIDING SYSTEM VALUE eklenir (yoksa);
      # identity'si olmayan tablolarda zararsiz. Hata olursa durur (ON_ERROR_STOP).
      sed -E 's/^(INSERT INTO [^ ]+ \([^)]*\)) VALUES /\1 OVERRIDING SYSTEM VALUE VALUES /' "$f" \
        | docker exec -i "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -q -v ON_ERROR_STOP=1 > /dev/null
    done <<< "${TABLE_FILES[$t]}"
  fi

  NEWCNT=$(psql_c "SELECT count(*) FROM ${t};")
  echo "[import] ${t}: tamam (${NEWCNT} satir)."
done

# id'ler dosyadan ACIKCA yuklendi; IDENTITY sayaclari (solver_run.id,
# path_explorer_solution.id) ilerlemedi. Ayarlanmazsa import sonrasi ilk yeni
# kayit id=1 ile cakisir (duplicate key). Partition'lar parent'in sayacini
# paylastigi icin sadece ust tablolara bakilir.
echo "[import] IDENTITY sayaclari max(id)'ye ayarlaniyor..."
docker exec "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -q -c "
DO \$\$
DECLARE r record; m bigint;
BEGIN
  FOR r IN
    SELECT pg_get_serial_sequence(format('public.%I', c.table_name), c.column_name) AS seq,
           c.table_name AS tbl, c.column_name AS col
    FROM information_schema.columns c
    JOIN pg_class k ON k.relname = c.table_name AND k.relkind IN ('r', 'p')
    LEFT JOIN pg_inherits i ON i.inhrelid = k.oid
    WHERE c.table_schema = 'public' AND c.is_identity = 'YES' AND i.inhrelid IS NULL
  LOOP
    CONTINUE WHEN r.seq IS NULL;
    EXECUTE format('SELECT coalesce(max(%I), 0) FROM public.%I', r.col, r.tbl) INTO m;
    PERFORM setval(r.seq, greatest(m, 1), m > 0);
    RAISE NOTICE '% -> %', r.seq, m;
  END LOOP;
END
\$\$;"

echo "[import] Bitti."
