# Calisma Ortami (Donanim ve Yazilim)

Cozum sureleri (`solving_checkpoint.elapsed`, `created_at`) bu makinede olculdu.
Baska bir makinede ayni grid ayni sayida cozum/adim uretir (cozucu deterministik)
ama SURELER degisir. Makalede/raporda sureler verilirken bu bilgiler birlikte verilmeli.

Bilgiler 2026-10-01'de makineden okundu (`Win32_*` CIM siniflari, `Get-PhysicalDisk`,
`java -XshowSettings`, `docker info`, `select version()`).

## 1. Sistem
- Bilgisayar
  - Uretici / model : Dell OptiPlex SFF Plus 7010 (Small Form Factor masaustu)
  - Anakart : Dell 0YGWFV
  - BIOS : Dell 1.11.0 (2023-12-19)
  - Mimari : x64

## 2. Islemci (CPU)
- Model
  - Islemci : Intel Core i7-13700 (13. nesil, "Raptor Lake")
  - Soket sayisi : 1
- Cekirdek / thread
  - Fiziksel cekirdek : 16 (8 Performance + 8 Efficient)
  - Mantiksal islemci (thread) : 24 (P-cekirdekler Hyper-Threading'li)
- Saat hizi
  - Taban frekans (P-cekirdek) : 2,1 GHz (Windows'un raporladigi MaxClockSpeed = 2100 MHz)
  - Maks. turbo : 5,2 GHz (Intel spesifikasyonu)
- Onbellek
  - L2 : 24 MB (toplam)
  - L3 : 30 MB (paylasimli)
- Guc
  - Taban guc (TDP) : 65 W; maks. turbo gucu 219 W (Intel spesifikasyonu)
  - Windows guc plani : Dengeli (Balanced)

## 3. Bellek (RAM)
- Kapasite
  - Toplam : 32 GB (34.033.348.608 bayt kullanilabilir)
  - Modul : 1 x 32 GB (DIMM1) - tek kanal
- Tip / hiz
  - Tip : DDR5
  - Modul hizi : 4800 MT/s (nominal)
  - Calisan hiz : 4400 MT/s (ConfiguredClockSpeed)
  - Uretici / parca no : Micron MTC16C2085S1UC48BA1

## 4. Depolama
- Disk
  - Model : Samsung PM9A1 NVMe SSD
  - Arayuz : NVMe (PCIe 4.0)
  - Kapasite : 512 GB (C:, NTFS; olcum aninda ~295 GB bos)
- Kullanim
  - PostgreSQL verisi Docker volume'unda (WSL2 sanal diski, ayni SSD uzerinde)
  - Backup: `rapor/backup/` (pgdump ~300 MB, 6x6 cozum CSV'si ~1,1 GB)

## 5. Ekran karti
- GPU : Intel UHD Graphics 770 (dahili) - cozucu GPU KULLANMIYOR

## 6. Isletim sistemi
- Windows
  - Surum : Windows 11 Enterprise, 64 bit
  - Build : 10.0.22621
- Sanallastirma (veritabani icin)
  - Docker Desktop 29.0.1, WSL2 cekirdegi 6.6.87.2-microsoft-standard-WSL2
  - Docker'a ayrilan kaynak : 24 CPU, ~15,5 GB RAM

## 7. Yazilim
- Java
  - JDK : JetBrains Runtime (JBR) 21.0.11 (OpenJDK 64-Bit Server VM, build 21.0.11+1-b1163.116)
  - Calistirma : IntelliJ IDEA 2025.2.4 icinden, ek JVM parametresi yok
  - Heap : varsayilan (baslangic ~508 MB, maks. ~7,9 GB = RAM'in 1/4'u)
  - GC : G1 (varsayilan)
- Veritabani
  - PostgreSQL 16.15 (Debian, Docker imaji `postgres:16`), port 5443
  - JDBC : PostgreSQL JDBC 42.7.4, HikariCP 5.1.0 (havuz boyutu 1)

## 8. Cozucu ozellikleri (surelerin yorumu icin)
- Paralellik
  - Cozucu TEK THREAD calisiyor (Algoritma 2, `PlayGame` ana dongusu). 24 thread'in
    sadece biri kullanilir; sureyi belirleyen tek cekirdek performansi.
  - i7-13700 hibrit: Windows thread'i P-cekirdekten (5,2 GHz) E-cekirdege (maks. 4,1 GHz)
    tasiyabilir. Bu, ayni makinede bile kayitlar arasi sure farkina yol acabilir.
- Bellek / disk etkisi
  - Cozucu state'i kucuk (49 kareli yol + bitset). RAM boyutu sureyi etkilemiyor;
    onbellek ve bellek gecikmesi etkiler.
  - Disk sadece checkpoint yazarken kullaniliyor (7x7: her 100.000 cozumde bir satir,
    8x8: her 1.000.000'da bir). Diskin sureye etkisi ihmal edilebilir.
- Olcum yontemi
  - `elapsed`: iki checkpoint arasindaki saf cozme suresi (`System.nanoTime`),
    process kapaliyken gecen sure dahil degil. Detay:
    `docker/initdb/09_solving_checkpoint_elapsed.sql`.
  - 2026-09-28 oncesi satirlarda elapsed `created_at` farkindan dolduruldu
    (ayni run icinde); her run'in ilk satiri NULL.
  - Makine olcum sirasinda baska islerde de kullanildi (IntelliJ, tarayici vb.);
    sureler "ozel, bos makine" degil, gunluk kullanim altinda olculdu.

## 9. Bu makinede olculen performans
- Hiz
  - ~2,3-2,4 milyon adim/sn (`round_counter` artisi / elapsed), 7x7 ve 8x8'de benzer
  - 7x7: cozum basina ~420-5.000 adim (bolgeye gore degisiyor)
  - 8x8 (0,0) karesi: ~10.600 cozum/sn (2026-09-25 olcumu)
- 7x7 (checkpoint_version 2)
  - 2026-09-30 itibariyla ~994 milyon cozum, ~66 saat toplam elapsed
  - 5 karenin bitmis sayilari ve sureleri: `SESSION-OZET.md`

## 10. Bilgileri yeniden almak (baska makine icin)
PowerShell:
```
Get-CimInstance Win32_Processor | Select Name,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed,L2CacheSize,L3CacheSize
Get-CimInstance Win32_ComputerSystem | Select Manufacturer,Model,TotalPhysicalMemory
Get-CimInstance Win32_PhysicalMemory | Select Manufacturer,PartNumber,Capacity,Speed,ConfiguredClockSpeed
Get-PhysicalDisk | Select FriendlyName,MediaType,BusType,Size
Get-CimInstance Win32_OperatingSystem | Select Caption,Version,BuildNumber
powercfg /getactivescheme
```
Java / DB:
```
java -XshowSettings:properties -version
docker exec dev-postgres psql -U pathexplorer -d pathexplorer -c "select version();"
```
