#!/usr/bin/env bash
# rapor/backup/{pgdump,csv,sql-insert} klasorlerinden birini secip pathexplorer
# DB'sine GERI YUKLER. docker-compose.yml'daki AYNI container/kullanici/db
# adlarini kullanir (PG_CONTAINER/PG_USER/PG_DB ile override edilebilir).
#
# YIKICI ISLEM: hedef tablolarda ZATEN VERI VARSA, once ONAY ister, sonra
# o tablolari TAMAMEN SILIP (TRUNCATE ... CASCADE) dosyadan yeniden doldurur.
# Boylece eski + yeni veri KARISMAZ. Onay verilmezse HICBIR SEY DEGISMEZ.
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

TABLES=()
for f in "${FILES[@]}"; do
  base="$(basename "$f")"
  if [ "$SRC" = "csv" ]; then
    TABLES+=("${base%.csv}")
  else
    TABLES+=("${base%_insert.txt}")
  fi
done

echo ""
echo "Su tablolar import edilecek (${SRC}):"
for t in "${TABLES[@]}"; do
  echo "  - $t"
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
PARENT_ORDER=(grid_map solving_algorithm solver_run)
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
    echo "[import] ${t}: CSV'den yukleniyor..."
    docker exec -i "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -c \
      "COPY ${t} FROM STDIN WITH CSV HEADER" < "${BACKUP_DIR}/csv/${t}.csv"
  else
    echo "[import] ${t}: SQL-insert'ten yukleniyor..."
    docker exec -i "$CONTAINER" psql -U "$PG_USER" -d "$PG_DB" < "${BACKUP_DIR}/sql-insert/${t}_insert.txt" > /dev/null
  fi

  NEWCNT=$(psql_c "SELECT count(*) FROM ${t};")
  echo "[import] ${t}: tamam (${NEWCNT} satir)."
done

echo "[import] Bitti."
