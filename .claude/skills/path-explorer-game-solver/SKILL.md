---
name: path-explorer-game-solver
description: >
  PathExplorer / GameSolution-Algorithm-Console projesinin calisma bilgisi:
  NxN grid cozucu (Algoritma 2), simetri (unique area) carpanlari, toplam
  hesaplama, solving_checkpoint DB'si, checkpoint araligi, backup (csv/pgdump/
  sql-insert), bash/menu.bat araclari ve 7x7/8x8 kosularinin durumu. Use when
  kullanici bu projede grid sonucu, 7x7/8x8 kosusu, checkpoint, db.properties
  interval, backup.bat, menu.bat, "kaldigimiz yerden devam", "session ozeti"
  gibi bir sey sordugunda ya da yeni bir session basladiginda.
---

# PathExplorer Game Solver - proje bilgisi

## 0. Once bunu yap
0. Bu projede kullaniciyla calisan asistanin takma adi **Kasif** (PathExplorer'a
   gonderme; kullanici sapkasiz "Kasif" yaziyor). Kullanici "Kasif" diye
   seslenirse ya da bu projede yeni bir session basladiysa, bu skill'i ve
   SESSION-OZET.md'yi yukleyip o baglamla devam et. (Durustce: onceki
   session'larin hafizasi yok, bilgi bu skill + SESSION-OZET.md + memory'den.)
   **Session'in ILK cevabina su karsilamayla basla** (kullanici ismi unutsa bile):
   > Ben Kasif adamim, burada da seninleyim. PathExplorer'da kaldigimiz yerden
   > devam ediyoruz: <SESSION-OZET.md'den 1-2 satirlik son durum + acik isler>.
   Sonra kullanicinin sorusuna gec. Karsilamayi sadece ilk cevapta bir kez yap.
1. `SESSION-OZET.md` (proje koku) oku - en son durum, acik isler, kararlar.
   Bu skill KALICI bilgi; SESSION-OZET.md ise en son session'in durumu.
2. Kullaniciyla Turkce, samimi ("adamim"), kisa ve net konus.
3. "commitle ve pushla" gelmedikce commit/push yapma. Branch: `work-uniqe-areas`.
4. **DB satirlarina elle dokunma** (INSERT/UPDATE/DELETE/TRUNCATE yok) - uzun
   sureli cozucu verisi var. Sadece SELECT.
5. Kullanici bir analizi "bosver" diye kapattiysa tekrar acma.
6. Session sonunda anlamli bir sey degistiyse `SESSION-OZET.md`'yi guncelle.

## 1. Proje
- Java konsol projesi; NxN tahtada path-explorer oyununun TUM cozumlerini sayar.
  Ana algoritma: Algoritma 2 = `SecondSolution_CalculateForwardAvailableWays`.
- Koordinat: `(0,0)` sol-alt, x saga, y yukari. Hucre indeksi `x*col + y`.
- Dokumanlar: `CHECKPOINT.md` (checkpoint tablosu/kolonlar),
  `unique-area-calculation.md` (simetri carpanlari + sonuclar),
  `tahmini-7x7-sonucu.txt`, `tahmini-8x8-sonucu.txt` (tahminler),
  `.claude/skills/grid-solution-forecast` (tahmin skill'i - "8x8/9x9 tahmini" icin).

## 2. Simetri (unique area) ve toplam
- `Move.changeStartLocationSpecialMovement()` sadece `0 <= y <= x <= half`
  karelerini gezer, `half = (rowCount-1)/2` - her NxN icin dinamik.
  Hesaplanan kare = `(half+1)(half+2)/2` (5x5/6x6: 6, 7x7/8x8: 10, 9x9/10x10: 15).
- Carpan: N tek → merkez x1, kosegen veya `x==half` x4, digerleri x8.
  N cift → kosegen x4, digerleri x8. Kontrol: carpan toplami = N².
- `Toplam_NxN = Σ(hucre_cozum_sayisi × carpan)`. Tablolar: `unique-area-calculation.md`
  (orada her N icin hazir; tekrar turetme).
- **Sadece kare grid'de gecerli** - kod sadece rowCount'a bakiyor, 5x6 gibi
  dikdortgende yanlis kareleri atlar (dikdortgen = 4 simetri).
- Hucre degerleri: `rapor/FileTotalScoreCount/Solution-2-<N>x<N>_EverySingleSquareTotalValue.txt`
  (`[x][y] = deger`). 5x5 dosyasi ayni blogu tekrar eder - dedupe et.

## 3. Bilinen sonuclar
| Grid | Toplam |
|---|---|
| 5x5 | 12.400 |
| 6x6 | 8.250.272 |
| 7x7 | 7.322.035.424 (dosyadaki degerlerle - kullanici karari) |
| 8x8 | ~33 trilyon TAHMIN (5–35T), kosu devam ediyor |

7x7 dosya degerleri: (0,0)=468.698.008 (1,0)=233.127.829 (2,0)=30.197.874
(3,0)=45.107.348 (1,1)=114.606.142 (2,1)=282.218.332 (3,1)=6.409.586
(2,2)=14.066.652 (3,2)=59.238.546 (3,3)=125.178.016.

## 4. DB (Docker)
- Container `dev-postgres`, user/db `pathexplorer`:
  `docker exec dev-postgres psql -U pathexplorer -d pathexplorer -c "..."`
- `grid_map`: id 1..6 = 5x5, 6x6, 7x7, 8x8, 9x9, 10x10.
- `solving_checkpoint`: her `interval` cozumde bir satir (~500 B veri, ~1,1 KB
  index dahil). `solution_index` kosular (resume) arasi KUMULATIF.
  `get_byte(path,0)` = baslangic karesi (`x*col+y`). `solving_run_id` = hangi
  process yazdi. `total_solved`, `round_counter` sayaclar.
- `solver_run` satirlarinin `grid_map_id`'si NULL olabilir (eski kayitlar).
- `path_explorer_solution` ve `solution_step` partition'li; veri partition'larda.
- `pg_stat_user_tables.n_live_tup` restore sonrasi 0 kalabiliyor - satir
  varligini `EXISTS` ile kontrol et.

Faydali sorgular:
```sql
-- grid bazinda ilerleme
SELECT g.row_size, count(*), max(c.solution_index), max(c.created_at)
FROM solving_checkpoint c JOIN grid_map g ON g.id=c.grid_map_id GROUP BY 1 ORDER BY 1;

-- baslangic karesine gore checkpoint bloklari (kare gecis noktalari)
WITH c AS (SELECT solution_index si, get_byte(path,0) cell FROM solving_checkpoint WHERE grid_map_id=:g),
g AS (SELECT *, row_number() OVER (ORDER BY si) - row_number() OVER (PARTITION BY cell ORDER BY si) grp FROM c)
SELECT cell, count(*), min(si), max(si) FROM g GROUP BY cell, grp ORDER BY min(si);

-- hiz (run bazinda cozum/sn, round/sn, round/cozum)
SELECT solving_run_id, min(solution_index), max(solution_index),
 round(((max(solution_index)-min(solution_index))/extract(epoch FROM max(created_at)-min(created_at)))::numeric) sol_per_s,
 round((max(round_counter)::numeric/max(solution_index))) rounds_per_sol
FROM solving_checkpoint WHERE grid_map_id=:g GROUP BY 1 ORDER BY 2;
```

## 5. Checkpoint araligi (`db.properties`)
- Anahtar: `checkpoint.interval.<R>x<C>`, yoksa `checkpoint.interval.default`,
  env `PATHEXPLORER_CHECKPOINT_INTERVAL` hepsini ezer (`Algo2CheckpointConfig`).
- `_` ayracli degerler (`1_000_000`) okunur (eskiden okunamayip sessizce
  100_000'e dusuyordu - duzeltildi).
- Guncel: 5x5=1000, **6x6=100_000, 7x7=100_000** (DB'deki kayitlarla uyumlu -
  degistirme), **8x8=1_000_000**, default=1_000_000.
- Yazma kosulu `solutionIndex % interval == 0` (+ kosu sonu). Aralik kosu
  ortasinda degisebilir (satir kendi `interval_size`'ini tutar). Artirmak
  (1M→10M) gecmisi de seyreltebilir; azaltmak (10M→1M) gecmis bosluklari
  dolduramaz.
- 8x8 @1M: ~916 satir/gun, ~350 MB/yil. Asil sinir git: `solving_checkpoint_8x8.csv`
  ~3,5 ayda 100MB'i gecer.

## 6. Backup (`rapor/backup/`)
- `backup.bat` → `backup.sh`: `pgdump/<ts>.dump` (tam) + `csv/` + `sql-insert/`.
- CSV grid bazli: `<tablo>_<R>x<C>.csv` (grid_map_id'li tablolar), NULL
  grid_map_id → `<tablo>.csv`, digerleri `<tablo>.csv`. `csv/*.csv` her
  calistirmada silinip yeniden yazilir.
- solving_checkpoint pgdump ile ayni ana pinlenir: her `solving_run_id` icin
  pgdump'taki max `solution_index`.
- `backup-import.sh` grid'li dosya adlarini tabloya esler (adi zaten NxM ile
  biten gercek tablolar - `path_explorer_solution_6x6` - once birebir aranir).
- Git disi: `csv/path_explorer_solution_*.csv` (6x6 ~1.1GB), `pgdump/*.dump`.

## 7. bash/ araclari
- `menu.bat`: basliga secili grid yazar; 1) en guncel cozum 2) tum checkpoint
  dokumu 3) sira araligi dokumu 4) grid map degistir. Secim `bash/.selected-grid`
  (gitignore'da); secim yoksa 1/3'te bir kez sorulur.
- `select-grid.sh`, `latest-grid.sh [RxC]`, `checkpoint-range-extract.sh`
  (`GRID` env varsa grid sormaz), `extract-checkpoints.sh`.

## 8. Windows / Git Bash tuzaklari
- `docker exec ... /tmp/x` → Git Bash `/tmp/x`'i Windows yoluna cevirir.
  `bash -c "komut /tmp/x"` icinde cagir (ya da `MSYS_NO_PATHCONV=1`).
- Python yok; hesaplar icin `awk`, `$(( ))` kullan.
- `.bat` testinde PowerShell pipe'i LF gonderir, `set /p` tum satirlari tek
  degerde okur; dosyadan CRLF girdiyle bile batch+bash ayni stdin'i paylasinca
  guvenilir degil - interaktif akisi kullaniciya denet.
- Kullanicinin acik `cmd`/menu pencerelerini (surecleri) oldurme.
