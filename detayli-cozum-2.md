# Detaylı Çözüm 2 — `SecondSolution_CalculateForwardAvailableWays`

Algoritma 2, Algoritma 1'in üstüne iki şey ekler:

1. **Sıralama:** En az çıkışı olan yöne önce gidilir.
2. **Budama:** Komşu karelerin çıkış sayısına bakılarak çözümü imkânsız dallara girilmez.

Oyun döngüsü, tahta, hamleler, başlangıç karesi değişimi ve bitiş kuralı `detayli-cozum-1.md` ile aynıdır.
Algoritmanın tamamı `MathFunctionForSecondSolution` sınıfı ile iki hareket sınıfında (`MoveForwardSecondSolution`, `MoveBackSecondSolution`) yaşar.

**Doğrulama.** Bu dosyadaki sözde kod ayrı bir Java programına çevrilip çalıştırıldı. 5x5 ALL taramasında dört sayaç da `GameRegressionTest` değerleriyle birebir tuttu:
çözüm 12.400, round 1.023.656, dummy back 83.076, total back 511.816. 5x5 UNIQUE sonucu da 2.816 çıktı.

---

## 1. Matematiksel temel

Sınıftaki kuralların hepsi tek bir gözleme dayanır. Kodda yazmaz ama isimler (`exit`, `oneWay`, `deadly`) bunu anlatır.

**Tanımlar.** Oyuncu `c` karesinde olsun, `c` ziyaret edilmiş sayılır. Boş bir `v` karesi için:

```text
w(v) = v'nin boş komşu sayısı      // kodda availableWayNumber
```

Graf yönsüz olduğu için `c`'nin boş komşuları, `c`'ye bağlı olan boş karelerin tamamıdır.

**Kural 1 — `w(v) = 0`, `v` son kare değil.** `v`'ye şimdi girilirse oyuncu orada kalır. Girilmezse `v`'ye bir daha ulaşılamaz. İki durumda da çözüm yoktur, geri dönülür.

**Kural 2 — `w(v) = 1`.** `v`'ye şimdi girilmezse `v`'nin tek bağlantısı kalır. Yola o bağlantıdan girilir ve çıkılamaz. Yani `v` **yolun son karesi olmak zorundadır**. Buna "bitiş adayı" diyelim, kodda "exit" budur.

**Kural 3 — Bitiş tektir.** Yolun tek sonu vardır. İki farklı kare aynı anda bitiş adayı olamaz.

Şimdi iki sayı tanımlayalım:

- `E` = daha önce kesinleşmiş bitiş adayı var mı (0 veya 1).
- `O` = şu anki karenin, bu adımda henüz denenmemiş ve `w = 1` olan komşu sayısı.

Bu adımda `O` karesinden en fazla birine girilebilir. Geri kalan `O - 1` tanesi bitiş adayı olur. Toplam bitiş adayı 1'i geçemez:

```text
E + (O - 1) ≤ 1     yani     E + O ≤ 2
```

Koddaki `CalculationDeadlyPoint` formülü bunun aynısıdır:

```text
1 - (E + O) / 2 ≥ 0     ⇔     E + O ≤ 2
```

**Sadece komşulara bakmak neden yeter?** Bir karenin `w` değeri ancak komşusu ziyaret edilince düşer. Son ziyaret edilen kare `c` olduğu için değeri yeni düşen kareler tam olarak `c`'nin komşularıdır.

**`E` ve `O` aynı kareyi iki kez saymaz.** Bitiş adayının tek boş komşusu ziyaret edilirse `w` değeri 0'a düşer ve Kural 1 yakalar. Bu yüzden kesinleşmiş aday, `w = 1` olarak yeniden görünmez.

**Bu algoritma neyi yapmaz.** Boş alanın adalara bölünmesini kontrol etmez. Bitiş adayının koordinatını da tutmaz, sadece `E` bitini tutar.

---

## 2. Veri yapıları

```text
Ağırlık: weight[n] = 8 - n          // n = 0..8, az çıkış = yüksek ağırlık

Navigation (bir adıma ait not):
    step                               // ait olduğu adım
    oneWayNumbersValue                 // o adımdaki O değeri
    compulsoryLocation                 // sonraki dönüşte gidilecek yön, GERİ veya null
    exitSituationWasLocatedInThisStep  // E bu adımda mı 1 yapıldı (geri alma için)

RoadMemory:
    exitSituation                      // E: EXIT_FREE = 0, EXIT_LOCATED = 1
    oneWayNumbersList                  // Navigation yığını, adımlar artan sırada
```

`RobotMemory` tek bir `RoadMemory` tutar. Checkpoint de `E` ile bu yığını kaydeder (`Algo2Snapshot`, `Algo2StateRestorer`), bu yüzden resume aynı karar akışını sürdürür.

---

## 3. `MathFunctionForSecondSolution` — ayrıntılı döküm

Sınıf **her round için yeniden oluşturulur**. Kalıcı bilgi sadece `RoadMemory`, `visitedAreas` ve `visitedDirections` içindedir.

### 3.1 Sabitler

| Sabit | Değer | Anlam |
|---|---:|---|
| `ONE_WAY_COUNT_MEANS_EXIT` | 2 | İkinci `w = 1` komşu görülünce o yön zorunlu yazılır. |
| `MAX_ONE_WAY_BEFORE_ABORT` | 3 | `O ≥ 3` ise `E + O ≤ 2` zaten bozuktur, tarama kesilir. |
| `MIN_ONE_WAY_TO_RECORD` | 1 | `O ≥ 1` ise Navigation yığına yazılır. |

### 3.2 Alanlar

| Alan | Başlangıç | Görev |
|---|---|---|
| `selectedDirection` | GERİ | Döndürülecek yön. |
| `lastLocation` | GERİ | Sabit referans. |
| `compulsoryLocation` | null | Yeni Navigation'a yazılacak zorunlu yön. |
| `oneWayNumbersValue` | 0 | Bu taramadaki `O`. |
| `killRequestByAvailableProcessFunction` | false | `O ≥ 3` olunca taramayı keser. |
| `navigation` | yeni nesne | Yığının tepesi veya yeni oluşturulan kayıt. |
| `totalSquareCount` | N² | Son adım kontrolü için. |
| `locationsList` | 9 yön | Tarama sırası. |

### 3.3 Metotlar

| Metot | Satır | Görev |
|---|---:|---|
| `calculateFunctionResult` | 54 | Ana karar. Bölüm 4. |
| `isNavigationInRoadMemoryAvailableForThisStep` | 84 | Yığın tepesi bu adıma mı ait. |
| `getLastNavigationFromRoadMemory` | 89 | Yığın tepesini verir, boşsa null. |
| `buildNavigation` | 97 | Şu anki `step`, `O`, `compulsoryLocation` ile kayıt üretir. |
| `calculateForwardAvailableDirectionsOfCurrentDirection` | 102 | 8 yön taraması. Bölüm 5. |
| `isExitSituationLocated` | 135 | `E == 1` mi. |
| `processAccordingToOneWayNumber` | 139 | `w = 1` komşu işlemi. Bölüm 6. |
| `isRequestedToKillFunctionByOneWayAvailableNumberProcess` | 158 | Kesme bayrağını okur. |
| `isAvailableWayEqualsToZero` | 162 | `w == 0` mı. |
| `isNextStepWillBeEqualsToTotalSquareValue` | 166 | `step == N² - 1` mi. |
| `calculateWeightResult` | 171 | `weight[w]` değerini verir. |
| `isOneWayNumberTooMuchToRunHealtyTheAlgorithm` | 175 | `O ≥ 3` mü. |
| `calculateDeadlyPoint` | 179 | `E + O ≤ 2` kontrolü. Bölüm 7. |
| `addNavigationToRoadMemoryList` | 183 | `O ≥ 1` ise yığına ekler. |

`w` değerini `InpectingForwardLocation.inspectLocationAndGetAvailableSquareNumbers` hesaplar. 8 yönde sınır ve `visitedAreas` kontrolü yapar, `visitedDirections` değerine bakmaz. Yani `w` tahtanın gerçek durumudur.

---

## 4. Ana karar — `calculateFunctionResult`

```text
FONKSİYON calculateFunctionResult():

    // A) Bu adıma daha önce gelindi ve kayıt var mı?
    nav = yığın tepesi
    EĞER nav != null VE nav.step == step:
        EĞER nav.O == 1 VE E == 1 VE nav.buAdımdaKilitlendi == false:
            nav.zorunlu = GERİ
        EĞER nav.zorunlu != null:
            DÖNDÜR nav.zorunlu              // tarama yapılmaz

    // B) Tarama
    tarama()                                // selected, O, compulsory güncellenir

    // C) Ölü nokta
    EĞER E + O ≤ 2:
        yeni = Navigation(step, O, compulsory)
        EĞER O ≥ 1: yığına ekle

    DÖNDÜR selected                         // E + O > 2 olsa bile, Bölüm 9
```

---

## 5. Tarama

```text
FONKSİYON tarama():
    enİyi = -1
    HER yön d, 0..7 sırasıyla:
        EĞER d uygun değil: DEVAM           // tahta dışı, dolu veya bu adımda denenmiş

        v = konum + d
        w = v'nin boş komşu sayısı

        EĞER w == 0 VE step != N² - 1:
            selected = GERİ
            ÇIK                             // Kural 1

        EĞER weight[w] > enİyi:
            enİyi = weight[w]
            selected = d                    // eşitlikte ilk görülen kalır

        EĞER w == 1:
            oneWayİşle(d)                   // Bölüm 6

        EĞER kesme bayrağı: ÇIK
```

Sıralama sadece dalların gezilme sırasını belirler, toplam çözüm sayısını değiştirmez. Bu, Warnsdorff kuralıyla aynı fikirdir.

Denenmiş yönler atlandığı için `O` sadece **henüz denenmemiş** `w = 1` komşuları sayar. Denenmiş olanların bilgisi Navigation ve `E` içinde durur.

---

## 6. Tek yol işleme — `processAccordingToOneWayNumber`

```text
FONKSİYON oneWayİşle(d):
    O += 1
    EĞER E == 1:   compulsory = GERİ
    EĞER O == 2:   compulsory = d           // üstteki atamayı ezer
    EĞER O >= 3:   selected = GERİ, kesme bayrağı = true
```

İlk `w = 1` komşu otomatik olarak `selected` olur. Ağırlığı 7'dir ve son adım dışında 8 ağırlık mümkün değildir, çünkü `w = 0` daha önce taramayı keser. İkinci `w = 1` komşu ağırlığı eşit olduğu için seçilmez, zorunlu yön olarak saklanır.

---

## 7. Ölü nokta — `CalculationDeadlyPoint`

```text
sonuç = 1 - (E + O) / 2
sonuç ≥ 0 ise IS_FREE_SO_MOVE_FORWARD (1), değilse IS_DEAD_SO_MOVE_BACK (-1)
```

| E | O | E+O | sonuç | Dönüş |
|---:|---:|---:|---:|---|
| 0 | 0 | 0 | 1.0 | FREE |
| 0 | 1 | 1 | 0.5 | FREE |
| 0 | 2 | 2 | 0.0 | FREE |
| 1 | 0 | 1 | 0.5 | FREE |
| 1 | 1 | 2 | 0.0 | FREE |
| 1 | 2 | 3 | -0.5 | DEAD |
| 0 | ≥3 | ≥3 | < 0 | DEAD (kesme ile) |
| 1 | ≥3 | ≥4 | < 0 | DEAD (kesme ile) |

`calculateFunctionResult` bu sonucu sadece **Navigation yazılsın mı** diye kullanır. Yönü değiştirmez.

---

## 8. Hareket kancaları ve yaşam döngüsü

Her iki hareket de `prepareAllStuff` içinde `navigation = yığın tepesi` alır.

### 8.1 İleri — `updateBeforeStep` (step artmadan önce)

```text
EĞER nav != null VE nav.step == step:
    EĞER nav.O == 2:
        EĞER E == 0:
            E = 1, nav.buAdımdaKilitlendi = true       // alınmayan tek yol bitiş olur
        EĞER gidilen yön == nav.zorunlu:
            nav.zorunlu = GERİ                         // ikinci kol da kullanıldı
    DEĞİLSE EĞER nav.O == 1 VE E == 1 VE kilit bu adımda değil:
        nav.zorunlu = GERİ
```

### 8.2 Geri — `updateAfterStep` (step azaldıktan sonra)

```text
// nav, hareket başlamadan alınan yığın tepesidir
EĞER nav != null VE nav.step == step:
    EĞER E == 0 VE nav.O == 1:
        E = 1, nav.buAdımdaKilitlendi = true           // denenen tek yol artık bitiş adayı

tepe = yığın tepesi
EĞER tepe != null VE step < tepe.step:
    yığından çıkar
    EĞER nav.buAdımdaKilitlendi: E = 0                 // geri alma
```

### 8.3 Tüm durumlar

İlk gelişteki tarama sonucuna göre:

| Durum | Seçilen | Yığına yazılan | Dönüşte ne olur |
|---|---|---|---|
| `w = 0` komşu var | GERİ | `O ≥ 1` ve FREE ise yazılır | Hemen geri gidildiği için aynı hamlede çıkarılır. |
| `O ≥ 3` | GERİ | yok | Geri. |
| `E=0, O=0` | En az `w` | yok | Yeniden tarama, sıradaki yön. |
| `E=1, O=0` | En az `w` | yok | Yeniden tarama, sıradaki yön. |
| `E=0, O=1` | Tek yol `D` | `{O1, null}` | `E=1` olur (`D` bitiş adayı), yeniden tarama ile diğer yönler denenir. |
| `E=1, O=1` | Tek yol `D` | `{O1, GERİ}` | Doğrudan GERİ. |
| `E=0, O=2` | İlk tek yol `A` | `{O2, B}` | İlerlerken `E=1`. Dönüşte `B`, sonra GERİ. |
| `E=1, O=2` | İlk tek yol `A` | yok (DEAD) | **Yine `A`'ya gidilir**, Bölüm 9. |

`E=0, O=2` durumunda `E` her iki kolda da 1 kalır ama anlamı değişir: `A` denenirken bitiş adayı `B`, `B` denenirken bitiş adayı `A` olur.

### 8.4 Değişmezler

- Yığında her adım için en fazla bir kayıt vardır ve adımlar kesin artandır.
- `E = 1` ise yığında `buAdımdaKilitlendi = true` olan tam bir kayıt vardır.
- `E`, kilitleyen Navigation yığından çıkınca 0 olur.
- Aynı adıma dönüldüğünde tahta aynıdır. Bu yüzden `w` değerleri değişmez.

---

## 9. Kodun formülden saptığı iki yer

İkisi de **çözüm sayısını değiştirmiyor**, sadece gereksiz arama yaptırıyor. Ölçümler doğrulama programından alındı. Mevcut kod değiştirilmedi.

### 9.1 DEAD sonucu geri döndürmüyor

`calculateFunctionResult` satır 71-78: `DEAD` gelince Navigation yazılmaz ama `selectedDirection` GERİ yapılmaz. `E=1, O=2` durumunda robot `A`'ya gider, dönünce `A` denenmiş olduğu için `O=1` görünür ve `B`'ye de gider. İki dalda da çözüm olamaz.

5x5 ALL'da DEAD sonucu 28.183 kez oluşuyor. Bunların 26.896'sında robot ileri gidiyor.

Örnek, aşağıdaki izlemede round 16 ile 24 arası.

### 9.2 Eksik kilit

`MoveBackSecondSolution` kilidi `nav.step == step` şartıyla koyar. `nav` hareketten önceki yığın tepesidir. `D`'nin de adımında kayıt varsa tepe `D`'nin kaydıdır, şart tutmaz ve alttaki `{O1}` kaydı kilitlenmez. `D` bitiş adayı olduğu halde `E` 0 kalır.

Çözüm: yığından çıkarma sonrası yeni tepe bu adıma aitse ve `E == 0`, `O == 1` ise kilitlemek.

### 9.3 Ölçüm

| Varyant | 5x5 ALL round | Azalma | 6x6 UNIQUE round | Azalma |
|---|---:|---:|---:|---:|
| Mevcut kod | 1.023.656 | — | 245.055.447 | — |
| DEAD ise GERİ | 718.216 | %29,8 | 147.802.085 | %39,7 |
| Eksik kilit düzeltmesi | 816.488 | %20,2 | 184.302.711 | %24,8 |
| İkisi birlikte | 497.128 | %51,4 | 97.928.689 | %60,0 |

Dört varyantta da çözüm sayısı aynı: 5x5 ALL 12.400, 6x6 UNIQUE 1.421.678.

Round sayaçları değişeceği için bu düzeltmeler `GameRegressionTest` round değerlerini ve mevcut checkpoint satırlarıyla sayaç uyumunu bozar. Yeni algoritmada veya yeni `checkpoint_version` ile uygulanmalıdır.

---

## 10. Adım adım örnek (5x5, başlangıç (0,0))

Tarama sütununda her uygun yönün `w` değeri var. Yığın sütunu tur başındaki tepeyi gösterir, `c` zorunlu yöndür.

| Round | step | Konum | E | Yığın tepesi | Tarama | Karar | Açıklama |
|---:|---:|---|---:|---|---|---|---|
| 1 | 1 | (0,0) | 0 | — | N=2 NE=3 E=2 | N | En küçük `w`, eşitlikte ilk. |
| 2 | 2 | (0,3) | 0 | — | E=2 SE=2 | E | |
| 3 | 3 | (3,3) | 0 | — | S=1 SW=2 | S | `O=1`, kayıt `{s3 O1}`. |
| 8 | 8 | (0,1) | 0 | {s3 O1} | N=1 NE=2 | N | `O=1`, kayıt `{s8 O1}`. |
| 12 | 12 | (4,1) | 0 | {s8 O1} | W=1 NW=1 | W | `O=2`, kayıt `{s12 O2 cNW}`. İlerlerken `E=1`. |
| 13 | 13 | (1,1) | 1 | {s12 O2 cNW} | N=1 | N | `E=1, O=1`, kayıt `{s13 O1 cGERİ}`. |
| 16 | 16 | (1,0) | 1 | {s13 O1 cGERİ} | N=1 E=1 | N | `E+O=3`, DEAD ama N seçildi. |
| 18 | 18 | (4,3) | 1 | {s13 O1 cGERİ} | S=0 | GERİ | Kural 1. |
| 20 | 16 | (1,0) | 1 | {s13 O1 cGERİ} | E=1 | E | N denendi, `O=1`, kayıt `{s16 O1 cGERİ}`. |
| 24 | 16 | (1,0) | 1 | {s16 O1 cGERİ} | — | GERİ | Zorunlu yön, tarama yok. |
| 49 | 13 | (1,1) | 1 | {s13 O1 cGERİ} | — | GERİ | Zorunlu yön. Kayıt çıkarılır. |
| 50 | 12 | (4,1) | 1 | {s12 O2 cNW} | — | NW | Zorunlu yön. İlerlerken kayıt `cGERİ` olur. |

Round 16-24 arası Bölüm 9.1'deki boşa aramadır. Formüle uyulsaydı round 16'da GERİ gelirdi, 8 round kazanılırdı.

---

## 11. `sozde-kod.md` ile farklar

Eski dosya genel olarak doğru, şu noktalar yanlış veya eksik:

- Yönler komşu kare değil, 3 düz veya 2 çapraz atlamadır.
- 9. bölüm "Geri dön (… pratikte geri dönüş tetiklenir)" diyor. Gerçekte DEAD çoğunlukla ileri gider.
- 5. bölüm kesme bayrağında doğrudan döndürüyor. Kod formüle de uğrar, sonuç aynıdır çünkü `O ≥ 3` her zaman DEAD'dir.
- "2. tek yol muhtemel gerçek çıkıştır" yorumu ters. Bitiş adayı, o an **girilmeyen** tek yoldur.
- `w = 0` veya kesme ile çıkıldığında kısmi `O` ile kayıt yazılabilir, eski dosyada bu yok. Kayıt aynı hamlede yığından çıkar.

---

## 12. Üçüncü algoritma için zemin

Buradaki maddeler sadece mevcut kodun durumudur.

- `third/MathFunctionWithSpecialFeaturesForThirdSolution` bu sınıfın neredeyse birebir kopyası. Farklar: sayılar sabit yerine doğrudan yazılmış, son adım `edgeValue²` ile hesaplanıyor, zorunlu yön `try/catch` ile okunuyor. Bölüm 9'daki iki sapma orada da var.
- "Altın kare" fikrinin başlangıcı olan bitiş adayı kodda iki yerde belli oluyor. Koordinat ikisinde de tutulmuyor:
  - `MoveForwardSecondSolution.locateExitSituation`: alınmayan tek yol. Tarama sonucundaki `A` ve `B`'den gidilmeyen kare.
  - `MoveBackSecondSolution.processAccordingToOneWayNumber`: az önce dönülen `D` karesi.
- `E=0, O=2` kolları arasında geçişte bitiş adayı `B`'den `A`'ya değişir, `E` ise 1 kalır. Koordinat tutulursa bu geçiş de güncellenmelidir.
- `E` kaldırıldığı yer tektir: `MoveBackSecondSolution.clearLastIndexInNavigationList`.
- Hız notu: Sınıf her round yeniden kuruluyor ve her `LocationsList` 9 yeni nesne üretiyor. Doğrulama programı dizilerle yaklaşık 18 milyon round/sn yaptı, mevcut kodun notlardaki hızı yaklaşık 2,4 milyon round/sn. Karşılaştırma kabadır, checkpoint ve dosya yazımı doğrulama programında yoktur.
