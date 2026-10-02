# PathExplorer: NxN Grid'de Tüm Çözümlerin Sayımı

Oct 2, 2026 · @Ahmet Emin SAĞLIK

7x7 tahtada oyunun tam 8.642.871.600 farklı çözümü var; tek thread'le \~108 saatte sayıldı. 8x8 için tahmin \~42 trilyon (aralık 10–45 trilyon).

## 1. Problem

NxN bir tahtada bir kareden başlanır ve her kare **tam bir kez** ziyaret edilerek tahta doldurulur. Her hamle iki türden biridir:

- **Düz:** yatay ya da dikey 3 kare ötesine atlama (arada 2 kare boş kalır)
- **Çapraz:** çapraz 2 kare ötesine atlama (arada 1 kare boş kalır)

Bir çözüm, N² karenin hepsini dolaşan bir hamle sırasıdır. Saydığımız şey, tüm başlangıç kareleri dahil bu sıraların **toplam sayısı**.

Örnek (7x7'den gerçek bir çözümün başı): (1,0) → (4,0) → (6,2) → (6,5) → (4,3) → … 49 kare. Koordinatlarda (0,0) sol alt köşe; x sağa, y yukarı artar.

## 2. Yöntem

### Arama: Algoritma 2

Çözücü, tüm yolları eksiksiz gezen geri izlemeli bir derinlik-öncelikli aramadır (`SecondSolution_CalculateForwardAvailableWays`). Her turda ya ileri bir hamle yapılır ya da bir adım geri alınır.

- Her adımda, ulaşılabilir karelerin kaç serbest girişi kaldığına bakılır.
- Tek girişi kalan kareler "zorunlu" olarak işaretlenir; çıkmaz üretecek dallar erkenden kesilir.
- Arama deterministiktir: aynı durumdan devam edildiğinde aynı sırayla aynı çözümler üretilir.

### Simetri: 10 kareye indirme

Kare tahtanın 8 simetrisi (4 dönme + 4 yansıma) aynı sayıda çözüm verir. Bu yüzden sadece `0 ≤ y ≤ x ≤ half` bölgesindeki başlangıç kareleri gezilir (`half = (N−1)/2`, tam sayı bölme). 7x7 ve 8x8'de bu 10 karedir.

Her karenin sonucu, simetride kaç kareye karşılık geldiğiyle çarpılır:

- **N tek:** tam merkez ×1; köşegen ya da orta eksen ×4; diğerleri ×8
- **N çift:** köşegen ×4; diğerleri ×8

Kontrol: çarpanların toplamı her zaman N² (7x7 için 49). Bu kural sadece kare tahtada geçerli.

```latex
\text{Toplam}(N) = \sum_{\text{10 kare}} \text{çözüm}(x,y) \cdot \text{çarpan}(x,y)
```

## 3. Sonuçlar

### Toplamlar

| Grid | Kare | Toplam çözüm | Durum |
| --- | --- | --- | --- |
| 5x5 | 25 | 12.400 | doğrulandı |
| 6x6 | 36 | 8.250.272 | doğrulandı |
| 7x7 | 49 | 8.642.871.600 | doğrulandı (2026-10-02 bitti) |
| 8x8 | 64 | \~42 trilyon | tahmin (bölüm 4) |

### 7x7, kare kare

Tek koşu, 2026-09-27 → 2026-10-02, checkpoint\_version 2. Bir karenin çözüm sayısı = o karenin son `solution_index`'i eksi bir önceki karenin son `solution_index`'i.

| Kare (x,y) | Çözüm | Çarpan | Katkı |
| --- | --- | --- | --- |
| (0,0) | 468.698.008 | ×4 | 1.874.792.032 |
| (1,0) | 233.127.829 | ×8 | 1.865.022.632 |
| (2,0) | 133.472.037 | ×8 | 1.067.776.296 |
| (3,0) | 45.107.348 | ×4 | 180.429.392 |
| (1,1) | 114.606.142 | ×4 | 458.424.568 |
| (2,1) | 282.218.332 | ×8 | 2.257.746.656 |
| (3,1) | 80.679.890 | ×4 | 322.719.560 |
| (2,2) | 63.457.066 | ×4 | 253.828.264 |
| (3,2) | 59.238.546 | ×4 | 236.954.184 |
| (3,3) | 125.178.016 | ×1 | 125.178.016 |
| **Toplam** | **1.605.783.214** | **49** | **8.642.871.600** |

Bu koşu 922.188.543.525 hamle yaptı; 461.094.271.734'tü geri adım. Çözüm başına ortalama \~574 hamle, ama bölgeye göre 420 ile \~5.000 arasında değişiyor.

Eski bir koşu 7x7 için 7.322.035.424 vermişti. O koşuda yeniden başlatmalarda kare sayacı sıfırlandığı için (2,0), (3,1) ve (2,2) eksik yazılmıştı; diğer 7 kare yeni koşuyla birebir aynı.

## 4. 8x8 tahmini

8x8 henüz sayılmadı; tahmin \~42 trilyon, makul aralık 10–45 trilyon. Bu bir ölçüm değil, 5x5–7x7'den ekstrapolasyon.

### Model

Toplamın kare sayısıyla üstel büyüdüğü varsayılır; ln(Toplam) ile N² arasına 3 noktayla en küçük kareler doğrusu oturtulur.

```latex
\text{Toplam}(N) = A \cdot b^{N^2}, \quad A = 0{,}011676, \quad b = 1{,}7504
```

| Yöntem | 8x8 toplam |
| --- | --- |
| 3 noktalı regresyon (ana tahmin) | \~42,4 trilyon |
| Kare kare regresyon (bağımsız kontrol) | \~40,7 trilyon |
| Son geçişin eğimi (1,7073) aynen sürerse | \~26,4 trilyon |
| Eğimdeki yavaşlama doğrusal sürerse (1,6090) | \~10,8 trilyon |

Kare başına büyüme yavaşlıyor (5→6: 1,806; 6→7: 1,707). Bu yüzden regresyon büyük ihtimalle üst sınıra yakın.

### Koşulacak kısım ve süre

- Gezilecek 10 karenin toplamı (wedge): \~1,8–7,2 trilyon çözüm.
- Bu makinede hız 4.000–10.600 çözüm/sn (7x7 ortalaması \~4.140; 8x8 (0,0) karesinde \~10.600 ölçüldü).
- Tek thread'le süre \~5–57 yıl, en olası \~20 yıl. Kareleri ayrı süreçlerde paralel koşmadan bitmesi gerçekçi değil.

Tek gerçek 8x8 verisi: eski bir koşu (0,0) karesinde 1.575.672.697 çözüme kadar gelip durduruldu. Bu, (0,0) için tahmin edilen \~2,41 trilyonun \~%0,07'si; tahminle çelişmiyor. Ayrıntılı tablo: repo'da `tahmini-8x8-sonucu.txt`.

## 5. Ölçüm ve çalışma ortamı

Çözüm sayıları makineden bağımsız; süreler ise bu makineye ait. Başka bir makinede aynı sayılar çıkar, süreler değişir.

### Süre nasıl ölçülüyor

- Çözücü her 100.000 çözümde bir (8x8'de 1.000.000) durumunu `solving_checkpoint` tablosuna yazar.
- `elapsed` kolonu, bir önceki kayıttan bu kayda kadar geçen **saf çözme süresidir** (`System.nanoTime`). Program kapalıyken geçen süre dahil değil.
- `created_at` ise duvar saatidir ve UTC saklanır (`+00`); Türkiye saati = UTC+3.
- 7x7'nin toplam süresi `sum(elapsed)` = \~107 sa 48 dk. 2026-09-28 öncesi satırlar aynı koşu içindeki `created_at` farkından dolduruldu; her koşunun ilk satırı (4 satır, birkaç dakika) boş.
- Sayaçların hepsi 64 bit (`long` / `BIGINT`); 2,4 milyon adım/sn hızla dolmaları \~120.000 yıl sürer.

### checkpoint\_version

Her satır, yazma mantığının sürümünü taşır. Sürüm 2'de kare değiştiğinde önceki karenin son çözümü de yazılır, böylece kare başına sayılar DB'den tam çıkar. `db.properties` içinde `checkpoint.version=2` olduğu için **her grid'deki yeni koşular sürüm 2 ile ve `elapsed` dolu yazılır**. Eski bir sürüm 1 koşusuna devam edilirse o satırlar sürüm 1 kalır.

### Donanım ve yazılım

| Bileşen | Değer |
| --- | --- |
| Bilgisayar | Dell OptiPlex SFF Plus 7010 |
| İşlemci | Intel Core i7-13700: 16 çekirdek (8P + 8E), 24 thread, 2,1 GHz taban / 5,2 GHz turbo, 30 MB L3 |
| Bellek | 32 GB DDR5, tek modül (tek kanal), 4400 MT/s |
| Disk | Samsung PM9A1 512 GB NVMe SSD |
| İşletim sistemi | Windows 11 Enterprise (build 22621), Docker Desktop + WSL2 |
| Java | JetBrains Runtime 21.0.11, varsayılan heap, G1 GC |
| Veritabanı | PostgreSQL 16.15 (Docker) |

Çözücü **tek thread** çalışır; süreyi belirleyen tek çekirdeğin hızıdır. Disk ve RAM boyutunun etkisi ihmal edilebilir. Hibrit işlemcide Windows thread'i P- ve E-çekirdekler arasında taşıyabildiği için süreler biraz oynar. Ölçümler makine günlük işlerde de kullanılırken yapıldı. Tam liste: repo'da `calisma-ortami.md`.

## 6. Nasıl çalıştırılır

Kod: [GameSolution-Algorithm-Console](https://github.com/AhmetEminSaglik/GameSolution-Algorithm-Console), branch `work-uniqe-areas`. Gerekenler: Java 21, Maven, Docker.

### Kurulum

1. Repo'yu al ve branch'e geç: `git clone …` ve `git checkout work-uniqe-areas`.
2. Veritabanını başlat: `docker compose up -d`. PostgreSQL 5443 portunda açılır (kullanıcı/DB: `pathexplorer`); `docker/initdb/*.sql` şema ve migration'ları ilk açılışta kendisi uygular.
3. Mevcut verileri istersen yükle: `rapor/backup/backup-import.sh` (pgdump, csv ya da sql-insert klasöründen). Dolu tabloları onay alıp siler ve yeniden doldurur.
4. Derle: `mvn package` → `target/game-solution-algorithm.jar`. Ya da IntelliJ'de `Main.Main`'i çalıştır.
5. Ayarlar `db.properties` içinde: bağlantı, `checkpoint.interval.<N>x<N>`, `checkpoint.version=2`.

### Bir grid'i baştan saymak

`java -jar target/game-solution-algorithm.jar` ve menüde sırayla:

1. Grid boyutu: `8` (dikdörtgen için `5x6`, ama simetri kuralı sadece karede doğru)
2. Oyuncu: `2` (Robot)
3. Algoritma: `2` (Calculate Forward Ways)
4. Başlangıç: `1` (baştan)
5. DB kayıt modu: `3` (checkpoint)

### Kaldığı yerden devam etmek

Aynı menüde Başlangıç = `2` seç, DB modu `3`, sürüm `2`, sonra listedeki **en son** checkpoint numarasını gir. Çözücü sayaçlarıyla birlikte o durumu geri yükler ve devam eder.

Aynı grid için **aynı anda iki çözücü çalıştırma**. IntelliJ kapanınca altındaki `java.exe` sahipsiz kalıp yazmaya devam edebiliyor; yeni başlatılan koşu o zaman aynı durumları tekrar üretip `[SKIP]` basar. Önce Görev Yöneticisi'nden eski `java.exe`'yi kapat.

### Faydalı sorgular

Kare kare ilerleme ve süre (grid\_map\_id: 1..6 = 5x5..10x10):

```sql
SELECT first_location, min(solution_index), max(solution_index), sum(elapsed)
FROM solving_checkpoint
WHERE grid_map_id = 4 AND checkpoint_version = 2
GROUP BY 1 ORDER BY 2;
```

Son kayıt, yerel saatle:

```sql
SELECT solution_index, first_location, elapsed,
       created_at AT TIME ZONE 'Europe/Istanbul' AS yerel
FROM solving_checkpoint
WHERE grid_map_id = 4 AND checkpoint_version = 2
ORDER BY solution_index DESC LIMIT 5;
```

Bu sorgularla sadece oku; çalışan çözücünün satırlarını elle değiştirme.

## 7. Açık konular ve sınırlamalar

- **8x8 paralelleştirme:** 10 başlangıç karesi birbirinden bağımsız; her biri ayrı bir süreçte (24 thread'lik makinede 10 paralel koşu) sayılabilir.
- **Dikdörtgen grid:** simetri kuralı sadece karede doğru. 5x6 gibi bir tahtada sadece 4 simetri var; kod bunu henüz ayırt etmiyor.
- **Eski notlar:** `PERSISTENCE.md` ve `todo-checklist.md` hâlâ "7x7 \~2 milyar" diyor; doğrusu 8.642.871.600.
- **Yedek boyutu:** `solving_checkpoint` CSV yedekleri GitHub'ın dosya başına 100 MB sınırına yaklaşınca (8x8 koşarken) bölünmeli ya da git dışında tutulmalı.
- **8x8 tahmini:** (0,0) karesi bitince gerçek değerle yeniden hesaplanmalı.
