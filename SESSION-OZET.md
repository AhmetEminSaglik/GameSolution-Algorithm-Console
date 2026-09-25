# Session Ozeti (2026-09-23 → 2026-09-25) — sonraki Claude session'i icin

> Yeni session'da once bunu oku, sonra kaldigimiz yerden devam et. Kullaniciyla
> Turkce, samimi ("adamim") konusuluyor; kisa, net cevap bekliyor.
> Asistanin bu projedeki takma adi **Kasif**. Kalici proje bilgisi skill'de:
> `.claude/skills/path-explorer-game-solver/SKILL.md` (repo'da, her PC'de var).

## Proje baglami (kisa)
- NxN grid'de path-explorer oyununun TUM cozumlerini sayan Java konsol projesi.
  Algoritma 2 (`SecondSolution_CalculateForwardAvailableWays`) kullaniliyor.
- DB: Docker `dev-postgres`, user/db `pathexplorer`. Ana tablo `solving_checkpoint`
  (her `interval` cozumde bir snapshot). `grid_map`: id 1..6 = 5x5..10x10.
- Branch: `work-uniqe-areas` (main: `master`). Kullanici "commitle ve pushla"
  deyince commit + push yapiliyor.
- **DB'ye elle dokunma** (kullanici acikca soyledi: kayitlar var).

## Simetri (unique area) mantigi
- Detay: `unique-area-calculation.md` (5x5..10x10 carpan tablolari + 7x7 harfli
  harita + 7x7 gercek sonuc). `Move.changeStartLocationSpecialMovement()` sadece
  `0 <= y <= x <= half` (`half=(rowCount-1)/2`) karelerini geziyor; her NxN icin
  dinamik. Hesaplanan kare sayisi = `(half+1)(half+2)/2` (7x7 ve 8x8'de 10).
- N tek: merkez x1, kosegen/orta eksen x4, digerleri x8. N cift: kosegen x4,
  digerleri x8. Carpan toplami = N².
- **Sadece KARE grid'de dogru.** Kod sadece rowCount'a bakiyor; 5x6 gibi
  dikdortgende yanlis olur (henuz duzeltilmedi, gerekirse yapilacak).

## Sonuclar
| Grid | Toplam | Not |
|---|---|---|
| 5x5 | 12.400 | dogrulandi |
| 6x6 | 8.250.272 | dogrulandi |
| 7x7 | **7.322.035.424** | `rapor/FileTotalScoreCount/Solution-2-7x7_EverySingleSquareTotalValue.txt` degerleriyle |
| 8x8 | ~33 trilyon (TAHMIN, aralik 5–35T) | `tahmini-8x8-sonucu.txt` |

- **7x7 icin kullanicinin karari:** dosyadaki 10 deger esas alinir, DB'den
  yeniden turetme YAPMA. (Ben DB'den C/G/H'nin resume yuzunden eksik yazildigini
  iddia etmistim → toplam 8.642.871.600 demistim; kullanici "bosver, dosyadaki
  degerleri kullan" dedi. Konuyu tekrar acma, sadece sorulursa bahset.)
  Bilgi: dosyadaki wedge toplami 1.378.848.333, DB son solution_index 1.605.783.214.

## 8x8 kosusu (DEVAM EDIYOR)
- 2026-09-24 14:27'de basladi, run `9ac44ac0-...`. 2026-09-25 sabahi ~584M cozum,
  hala (0,0) karesinde.
- Hiz: ~10.600 cozum/sn (7x7'nin ~2 kati). Normal: round/sn ayni (~2,4M), ama
  8x8'de cozum basina ~224 round (7x7'de 420–574). Kose kare en verimli kare.
- Tahmin: sadece (0,0) ~2,4 trilyon → bu hizla ~2,5 yil.

## Checkpoint araligi (interval)
- `db.properties`: 5x5=1000, **6x6=100_000, 7x7=100_000** (DB'deki mevcut
  kayitlarla uyumlu, DEGISTIRME), **8x8=1_000_000**, default=1_000_000.
- Duzeltilen bug: `Algo2CheckpointConfig.parseNullableInt` `1_000_000`'u
  okuyamiyor, sessizce 100_000'e dusuyordu → artik `_` siliniyor.
- 8x8 @1M: ~916 satir/gun, ~1,1 KB/satir (index dahil) → ~350 MB/yil. DB icin
  sorun yok. Asil sinir: `rapor/backup/csv/solving_checkpoint_8x8.csv` ~3,5 ayda
  GitHub 100MB sinirini gecer → o zaman gitignore'a al ya da parcala.
- Karar: simdilik 1M devam. Ileride 10M'e cikmak mumkun:
  - Yazma kosulu `solutionIndex % interval == 0` (`Algo2CheckpointWriter`), her
    satir kendi `interval_size`'ini tutuyor → aralik kosu ortasinda degisebilir.
  - 1M → 10M: ileriye donuk seyrekler; istenirse gecmis 1M satirlari da
    `solution_index % 10_000_000 <> 0` olanlar silinerek seyreltilebilir.
  - 10M → 1M: sadece ileriye donuk sikilasir; gecmisteki bosluklar geri
    doldurulamaz. Bu yuzden 1M'den baslamak guvenli secimdi.
  - Yan etki: Main'deki checkpoint listesi guncel interval'e uymayan satirlari
    "ara durak" diye gosterir (kozmetik).

## Backup (`rapor/backup/`)
- `backup.bat` → `backup.sh`: pgdump + csv + sql-insert.
  - CSV'ler grid'e gore ayriliyor: `<tablo>_<R>x<C>.csv`
    (orn. `solving_checkpoint_7x7.csv`); `grid_map_id` NULL satirlar `<tablo>.csv`
    (solver_run boyle).
  - solving_checkpoint pin'i her `solving_run_id` icin ayri (pgdump ile ayni an).
  - Tablo secimi `EXISTS` ile (pg_stat n_live_tup guvenilmez), partition parent'lari atlanir.
  - Git Bash'te `docker exec ... /tmp/...` argumani Windows yoluna cevriliyor →
    `bash -c "..."` icinde cagir.
- `backup-import.sh` grid'li CSV dosya adlarini dogru tabloya esliyor.
- Git'e girmeyenler: `csv/path_explorer_solution_6x6.csv` (~1.1GB), `pgdump/`.

## bash/menu.bat (DB araclari menusu)
- Basliga secili grid yaziliyor (`PathExplorer DB Araclari  7x7`).
- 4) Grid map degistir → `bash/select-grid.sh`, secim `bash/.selected-grid`'de
  (gitignore'da). Secim yoksa 1/3'e girince BIR KEZ soruluyor.
- `latest-grid.sh` ve `checkpoint-range-extract.sh` `GRID` env degiskenini kullaniyor.
- Parca parca test edildi; menunun uctan uca akisi gercek konsolda henuz denenmedi
  (kullanici deneyecek).
- Degisen: `.gitignore`, `bash/menu.bat`, `bash/latest-grid.sh`,
  `bash/checkpoint-range-extract.sh`, yeni `bash/select-grid.sh`.

## Acik isler / sonraki adimlar
1. menu.bat degisiklikleri commitlendi; kullanici gercek konsolda uctan uca
   deneyecek (ozellikle grid secili degilken 1/3'e girince sorma adimi).
2. 8x8 (0,0) bitince `tahmini-8x8-sonucu.txt`'yi gercek degerle guncelle
   (`/grid-solution-forecast` skill'i var).
3. ~3 ay sonra 8x8 checkpoint CSV'sinin git boyutu (100MB) meselesi.
4. (Istege bagli) resume'da kare sayacinin sifirlanmasi (FileTotalScoreCount'a
   eksik yazma) — kullanici simdilik ilgilenmiyor.
5. (Istege bagli) dikdortgen grid icin simetri kurali.
6. Eski notlar (`PERSISTENCE.md`, `todo-checklist.md`) hala "7x7 ~2 milyar" diyor.

## Commit'ler (bu session)
`e97eef5` (kullanici) backup grid bazli CSV · `18e1ada` 7x7 harita + sonuc ·
`bbffbde` interval parse fix + 8x8=1M + 8x8 tahmini · `2e78a1e` backup NULL grid_map_id
