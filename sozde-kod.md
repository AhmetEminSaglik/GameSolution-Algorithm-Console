# İkinci Çözüm (Second Solution) Algoritması — Sözde Kod

Kaynak sınıflar:
- `solution/second/SecondSolution_CalculateForwardAvailableWays.java` (giriş noktası)
- `solution/second/MathFunctionForSecondSolution.java` (ana algoritma)
- `solution/second/CalculationDeadlyPoint.java` (ölü nokta formülü)
- `solution/second/exitsituation/ExitSituation.java`
- `solution/second/navigation/Navigation.java`, `NavigationService.java`
- `memory/RoadMemory.java` (navigation yığını + exit durumu hafızası)
- `move/fundamental/secondsolutionforrobot/MoveForwardSecondSolution.java`, `MoveBackSecondSolution.java`
- `weights/WeightOfAvailableWay.java`

---

## 1. Genel Fikir

Robot her adımda bulunduğu kareden çevresindeki 8 yönü (K, KD, D, GD, G, GB, B, KB) sırayla dener.
Her aday yön için "o yöne gidersem, oradan kaç farklı boş kareye daha gidebilirim?" sorusunun cevabı
sayılır (**ileri açıklık / availableWayNumber**). Bu sayı ne kadar **küçükse** o yön o kadar **riskli**
(çıkmaza yaklaşıyor demektir) ama aynı zamanda tercih **ağırlığı** o kadar **yüksek** olur — çünkü
algoritma dar geçitleri erkenden tüketmeyi (labirent mantığıyla "kolay yolu sona bırakmayı") tercih eder.

Eğer bir yönün ileri açıklığı **tam olarak 1** ise ("tek çıkışlı yol / one-way") bu özel olarak işaretlenir.
Aynı adımda **2 farklı tek-çıkışlı yön** bulunursa, bunlardan biri gerçek **çıkış (exit)** olabilir; bu an
"Exit Located" olarak hafızaya yazılır ve o yöne gitmek **zorunlu (compulsory)** hale gelir. Sonraki bir
adımda exit zaten bulunmuşken yeni bir tek-çıkışlı yol daha bulunursa, bu ikinci yol yanlış bir çıkmaz
olarak yorumlanır ve robot doğrudan **geri döndürülür**.

Bütün bu "tek-yol / exit" bilgisi `RoadMemory` içinde bir **yığın (stack)** gibi tutulan `Navigation`
kayıtları ile saklanır: ileri giderken push edilir, robot o adımın **altına** geri dönerse pop edilir ve
exit durumu da geri alınır (rollback). Böylece robot aynı adıma tekrar geldiğinde (backtrack sonucu)
hesaplamayı baştan yapmaz, önceden bulunmuş **zorunlu yönü** doğrudan kullanır.

---

## 2. Temel Veri Yapıları

```
DIRECTIONS  = [Kuzey, KuzeyDoğu, Doğu, GüneyDoğu, Güney, GüneyBatı, Batı, KuzeyBatı]  // 8 yön
LAST_LOCATION = özel yön değeri  // "geri dön" anlamına gelir

WeightOfDirection[0..7]:
    WeightOfDirection[n] = 8 - n     // açık yol sayısı (n) arttıkça ağırlık azalır

ExitSituation:
    EXIT_FREE    = 0     // henüz çıkış bulunmadı
    EXIT_LOCATED = 1     // muhtemel çıkış yönü bulundu ve kilitlendi

Navigation (bir adıma ait "hafıza notu"):
    step                              // bu navigation hangi adımda oluşturuldu
    oneWayNumbersValue                // bu adımda kaç farklı "tek çıkışlı" yön bulundu (0,1,2,...)
    compulsoryLocation                // varsa, zorunlu gidilecek/dönülecek yön (null olabilir)
    exitSituationWasLocatedInThisStep // exit durumu tam bu adımda mı kilitlendi (rollback için)

RoadMemory (robotun hafızası):
    exitSituation : ExitSituation
    oneWayNumbersList : Navigation[]   // STACK gibi kullanılır (son eleman = en tepe)
```

---

## 3. Genel Oyun Döngüsü (bağlam için, referans)

```
FONKSIYON playGame():
    ADIM = 0
    KADAR (oyun bitmedi):
        yön = robot.solution.getLocationInput()       // <-- İkinci çözümün kalbi burada çağrılır
        EĞER yön == LAST_LOCATION İSE:
            hareket = MoveBackSecondSolution
        DEĞİLSE:
            hareket = MoveForwardSecondSolution
        hareket.uygula(yön)   // konumu güncelle, ziyaret edilen alan/yön işaretle, ADIM +1 / -1
```

---

## 4. Giriş Noktası — `getLocationInput`

```
FONKSIYON SecondSolution.getLocationInput():
    playerLocation = robotun_su_anki_konumu()
    DÖNDÜR MathFunctionForSecondSolution(playerLocation).calculateFunctionResult()
```

---

## 5. Ana Algoritma — `calculateFunctionResult` ⭐ (ÇEKİRDEK MANTIK)

```
FONKSIYON calculateFunctionResult():

    lastLocation = "geri dön" yönü
    selectedDirection = lastLocation          // varsayılan: geri dön
    oneWayNumbersValue = 0
    compulsoryLocation = null

    // --- 1) Bu adım için daha önce hesaplanmış bir navigation var mı? ---
    // (robot backtrack sonucu aynı adıma tekrar gelmiş olabilir)
    navigation = RoadMemory.oneWayNumbersList.sonEleman()
    EĞER navigation != null VE navigation.step == robot.step İSE:

        // exit zaten kilitliyse ve bu tek-yol bilgisi bu adımda ilk defa
        // kilitlenmediyse -> zorunlu olarak geri dön
        EĞER navigation.oneWayNumbersValue == 1
            VE RoadMemory.exitSituation == EXIT_LOCATED
            VE DEĞİL(navigation.exitSituationWasLocatedInThisStep) İSE:
                navigation.compulsoryLocation = lastLocation

        EĞER navigation.compulsoryLocation != null İSE:
            DÖNDÜR navigation.compulsoryLocation.id   // önceden bulunmuş zorunlu yönü kullan, tekrar hesaplama yapma
        // (compulsoryLocation null ise normal hesaplamaya devam edilir)


    // --- 2) Bulunduğumuz kareden gidilebilecek yönleri tara ---
    calculateForwardAvailableDirectionsOfCurrentDirection()
    // bu fonksiyon: selectedDirection, oneWayNumbersValue, compulsoryLocation değerlerini günceller
    // (ayrıntı: Bölüm 6)

    EĞER "kill" bayrağı set edildiyse (Bölüm 6.2'de anlatılan durum) İSE:
        DÖNDÜR selectedDirection.id   // = lastLocation, doğrudan geri dön


    // --- 3) Ölü nokta hesabı: ileri gidilsin mi, geri mi dönülsün? ---
    sonuc = calculateDeadlyPoint(oneWayNumbersValue)   // Bölüm 7

    EĞER sonuc == IS_FREE_SO_MOVE_FORWARD İSE:
        navigation = Navigation(step=robot.step, oneWayNumbersValue, compulsoryLocation)
        EĞER oneWayNumbersValue >= 1 İSE:
            RoadMemory.oneWayNumbersList.push(navigation)   // ileriki backtrack için hafızaya yaz

    DÖNDÜR selectedDirection.id
```

---

## 6. Alt Algoritma — İleri Açık Yönleri Tarama

### 6.1. Ana tarama döngüsü

```
FONKSIYON calculateForwardAvailableDirectionsOfCurrentDirection():

    enIyiAgirlik = -1

    HER yön (i = Kuzey..KuzeyBatı, 8 adet, "geri dön" hariç) İÇİN:

        EĞER yön ziyaret_edilmemiş_kare VE ziyaret_edilmemiş_yön İSE:   // ilerlemeye uygun mu?

            // O yöne bir "drone" gönderip oradan kaç farklı boş kareye
            // gidilebileceğini say (mevcut ziyaret durumuna göre)
            availableWayNumber = o_yonden_ileriye_gidilebilecek_kare_sayisi(yön)

            // --- Çıkmaz sokak kontrolü ---
            EĞER availableWayNumber == 0 VE DEĞİL(bir_sonraki_adim_son_kare_mi()) İSE:
                selectedDirection = lastLocation
                DUR (fonksiyondan çık)              // bu yön tam çıkmaz, hemen vazgeç

            // --- Ağırlığa göre en iyi yönü güncelle ---
            EĞER WeightOfDirection[availableWayNumber] > enIyiAgirlik İSE:
                enIyiAgirlik = WeightOfDirection[availableWayNumber]
                selectedDirection = yön

            // --- Tek çıkışlı yol tespiti ---
            EĞER availableWayNumber == 1 İSE:
                processAccordingToOneWayNumber(yön)     // Bölüm 6.2

            EĞER "kill" bayrağı set edildiyse İSE:
                DUR (fonksiyondan çık)
```

> Not: `WeightOfDirection[n] = 8 - n` olduğu için, ileri açıklığı **az** olan yönler
> (dar geçitler, 1-2 kareye çıkanlar) daha **yüksek ağırlık** alır ve öncelikli seçilir.
> Amaç: dar/riskli yolları erken tüketip geniş alanları en sona bırakmak.

### 6.2. Tek çıkışlı yol / Exit tespiti — `processAccordingToOneWayNumber` ⭐

```
FONKSIYON processAccordingToOneWayNumber(yön):

    oneWayNumbersValue = oneWayNumbersValue + 1

    // Exit zaten bulunmuşsa: bu adımda karşılaşılan yeni "tek yol" güvenilmez -> geri dön
    EĞER RoadMemory.exitSituation == EXIT_LOCATED İSE:
        compulsoryLocation = lastLocation

    // Bu adımda RASTLANAN 2. tek-yol -> muhtemel gerçek çıkış budur, buraya git
    EĞER oneWayNumbersValue == 2 İSE:
        compulsoryLocation = yön

    // Aynı adımda 3 veya daha fazla tek-yol bulunursa algoritma güvenilir çalışamaz -> vazgeç
    EĞER oneWayNumbersValue >= 3 İSE:
        selectedDirection = lastLocation
        "kill" bayragini SET ET
        DUR (fonksiyondan çık)
```

---

## 7. Ölü Nokta Hesabı — `CalculationDeadlyPoint` ⭐⭐ (3. ÇÖZÜMDE TEKRAR KULLANILACAK FORMÜL)

```
SABİTLER: IS_DEAD_SO_MOVE_BACK = -1,  IS_FREE_SO_MOVE_FORWARD = 1

FONKSIYON calculateDeadlyPoint(oneWayNumbersValue):
    exitSituationDegeri = RoadMemory.exitSituation      // 0 (FREE) veya 1 (LOCATED)

    hesap = 1 - (exitSituationDegeri + oneWayNumbersValue) / 2

    EĞER hesap >= 0 İSE:
        DÖNDÜR IS_FREE_SO_MOVE_FORWARD    // ileri git
    DEĞİLSE:
        DÖNDÜR IS_DEAD_SO_MOVE_BACK       // geri dön
```

Formülün anlamı (`exitSituation + oneWayNumbersValue` toplamına göre):

| exitSituation | oneWayNumbersValue | toplam | hesap | sonuç |
|---:|---:|---:|---:|---|
| 0 (FREE) | 0 | 0 | 1.0  | İLERİ |
| 0 (FREE) | 1 | 1 | 0.5  | İLERİ |
| 0 (FREE) | 2 | 2 | 0.0  | İLERİ (sınırda) |
| 1 (LOCATED) | 1 | 2 | 0.0  | İLERİ (sınırda) |
| 1 (LOCATED) | 2 | 3 | -0.5 | GERİ (ölü nokta) |

(`oneWayNumbersValue >= 3` durumu zaten Bölüm 6.2'de daha erken "kill" ile geri döndürülüyor,
bu yüzden bu formüle pratikte en fazla `oneWayNumbersValue = 2` ile girilir.)

---

## 8. Hareket Sırasında Hafıza Güncellemeleri

Yön seçimi bittikten ve robot fiilen hareket ettikten sonra, `RoadMemory` içindeki
`Navigation` yığını ve `exitSituation` şu şekilde güncellenir:

### 8.1. İleri hareket — `MoveForwardSecondSolution.updateBeforeStep`

```
FONKSIYON ileriHareketOncesiGuncelle():
    navigation = RoadMemory.oneWayNumbersList.sonEleman()

    EĞER navigation != null VE navigation.step == robot.step İSE:

        EĞER navigation.oneWayNumbersValue == 2 İSE:
            EĞER RoadMemory.exitSituation == EXIT_FREE İSE:
                RoadMemory.exitSituation = EXIT_LOCATED
                navigation.exitSituationWasLocatedInThisStep = true

            EĞER gidilenYön == navigation.compulsoryLocation İSE:
                // zorunlu yönü zaten kullandık; ileride buraya dönersek artık geri dönülmeli
                navigation.compulsoryLocation = lastLocation

        DEĞİLSE EĞER navigation.oneWayNumbersValue == 1
                 VE RoadMemory.exitSituation == EXIT_LOCATED
                 VE DEĞİL(navigation.exitSituationWasLocatedInThisStep) İSE:
            navigation.compulsoryLocation = lastLocation
```

### 8.2. Geri hareket — `MoveBackSecondSolution.updateAfterStep`

```
FONKSIYON geriHareketSonrasiGuncelle():
    navigation = RoadMemory.oneWayNumbersList.sonEleman()

    // (a) Geri giderken exit hâlâ bulunmadıysa ve tam bu adımda 1 tek-yol tespiti varsa, kilitle
    EĞER navigation != null VE navigation.step == robot.step İSE:
        EĞER RoadMemory.exitSituation == EXIT_FREE VE navigation.oneWayNumbersValue == 1 İSE:
            RoadMemory.exitSituation = EXIT_LOCATED
            navigation.exitSituationWasLocatedInThisStep = true

    // (b) Yığın temizliği: robot artık kayıtlı navigation'ın AİT OLDUĞU adımın da altına indiyse
    //     (yani gerçekten o adımı geride bıraktıysa) o kaydı yığından çıkar
    EĞER navigation != null VE robot.step < navigation.step İSE:
        RoadMemory.oneWayNumbersList.pop()
        EĞER navigation.exitSituationWasLocatedInThisStep İSE:
            RoadMemory.exitSituation = EXIT_FREE     // rollback: exit kilidini de geri al
```

---

## 9. Özet Akış (metinsel diyagram)

```
[Adım başı]
     │
     ▼
Bu adım için hafızada zorunlu bir yön var mı? ──(evet)──► O yöne git (bitti)
     │ (hayır)
     ▼
8 yönü sırayla tara:
   - Ziyaret edilmemiş her yön için, o yönden ileride kaç kare açık say (n)
   - n == 0 ve son kare değilse ► ÇIKMAZ: hemen "geri dön" seç, dur
   - ağırlık(n) en yüksekse    ► bu yönü aday seç
   - n == 1                    ► "tek yol" say, 2. kez ise "zorunlu yön" işaretle,
                                  3. kez ise güvenilmez: "geri dön" seç, dur
     │
     ▼
Ölü nokta formülü: (exitDurumu + tekYolSayisi) toplamı 2'yi aşıyor mu?
   - Hayır ► İLERİ git, bulunan bilgiyi (tek yol/zorunlu yön) hafızaya yaz
   - Evet ► GERİ dön (aday yön yine de döndürülür ama pratikte geri dönüş tetiklenir)
     │
     ▼
[Hareket gerçekleşir] ► hareket sırasında exit/navigation hafızası yukarıdaki
                         8.1 / 8.2 kurallarına göre güncellenir
```

---

## 10. Üçüncü Çözüm İçin Not

Bu dosyadaki **Bölüm 6.2 (tek-yol/exit tespiti)** ve **Bölüm 7 (ölü nokta formülü)** ile
**Bölüm 8 (navigation stack push/pop + exit rollback)** mekanizması, 3. çözümde yeniden
kullanılacak asıl "hesaplama çekirdeği". Refactor sırasında bunları saf/bağımsız bir
fonksiyon/servis haline getirmek (örn. `DeadlyPointCalculator`, `OneWayExitTracker`) iyi bir
başlangıç noktası olabilir.
