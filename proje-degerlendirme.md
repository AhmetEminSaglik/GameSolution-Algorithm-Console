# Proje Değerlendirmesi — GameSolutionAlgorithmWithoutFrame

Bu inceleme `src/` altındaki ~90 Java dosyasının tamamının taranmasıyla
hazırlandı (dosya boyutları, tekrar eden kalıplar, yorum/ölü kod oranı,
hata yönetimi, interface kullanımı vb. sistematik olarak tarandı, en
kritik dosyalar tek tek okundu). Amaç: nerede sıkıntı var, neden sıkıntı,
ve nasıl düzeltilir — somut kod örnekleriyle.

> **Yeniden değerlendirme — 2026-08-31.** İlk sürümden bu yana yapılan
> temizlik turları (`yapilmasi-gereken-duzeltmeler.md`: Bölüm 1 `[R*]`,
> Bölüm 2 `[L*]` Trace + JOptionPane kaldırma, Bölüm 3 `[D*]` ölü kod,
> Bölüm 4 `[B*]` boolean; ayrıca `pom.xml` / Maven build) sonrası proje
> baştan tarandı. §1'de artık **Eski / Yeni** puan sütunları var; kriter
> bazında ne değişti §7'de. Kod tekrar tarandı: 103 dosya / ~4500 satır,
> `(Robot)` cast 12, `JOptionPane`/`ShowPanel` **0** (eskiden 27),
> `printStackTrace` 2 (eskiden 6), `System.out` 37 (eskiden 48), yazım
> hatası ~72 eşleşme (henüz hiç dokunulmadı), otomatik test **hâlâ 0**.
> Davranış her turda 5x5/6x6 metrikleriyle sabit tutuldu
> (`total Solved = 12_400` vs.), yani puan artışı algoritmayı bozmadan geldi.

---

## 1. Genel Puanlama

| Kriter | Eski | Yeni | Kısa gerekçe (yeni durum) |
|---|---|---|---|
| **Mimari / Tasarım** | 6/10 | **6.5/10** | `DirectionCompassValues` çift tanımı silindi, Person yolundaki `ClassCastException` riski `getSolutionName()` ile kapandı, `Model.getTotalSquareCount()` edge² tekrarını topladı, Maven modülü geldi. Ama çekirdek sorunlar duruyor: 12 `(Robot)` cast (`[A2]`), `MathFunctionForSecondSolution` hâlâ 5 iş / ~182 satır (`[A3]`), 8 yön sınıfı enum olmadı (`[A1]`), `SelectFirstSqaureToStart extends DirectionLocation` (`[A4]`). |
| **Çözüm Yöntemi (algoritma)** | 7.5/10 | **7.5/10** | Değişmedi — algoritmalara bilerek dokunulmadı, davranış birebir korundu. Hâlâ projenin en güçlü yönü. |
| **Kodlama Kalitesi** | 4/10 | **6/10** | Sistematik boolean anti-pattern (~22 metot) düzeltildi; 2. çözümdeki sessiz boş `catch` kalktı (`[R7]`); `JOptionPane`/`ShowPanel` (27→0) ve `sleep` paketi silindi; sıcak yoldaki `Math.pow`/`edge*edge` isimli metoda döndü; `[R1]`–`[R6]` gerçek bug'lar kapandı; `WeightOfAvailableWay` dizi taşması güvene alındı. Açık: ~72 yazım hatası (`[N*]`), çoğu magic number (`[M*]`), `ErrorMessage.throwError` genel `Exception` (`[X1]`), sıcak yolda `assert` (`[X8]`). |
| **Clean Code Uygunluğu** | 3.5/10 | **6.5/10** | En büyük hareket burada. Ölü/yorumlu kod: `PlayGame` 27→~0, `MathFunctionForSecondSolution` 18→0, `Robot` 16→0; 2 ölü dosya + öncekilerle 6 dosya silindi; ~9 kullanılmayan import, atanıp okunmayan alanlar kalktı. Boolean sadeleştirmesi diff'leri küçülttü. `Trace` (derleme-zamanı DCE'li, iyi belgelenmiş) dağınık debug'ın yerini aldı. `yapilmasi-gereken-duzeltmeler.md` izlenebilir bir remediation planı olarak eklendi. Açık: yazım hataları okunabilirliği hâlâ tırmalıyor, TR/EN yorum karışık (`[N6]`). |
| **Test Edilebilirlik / Güvenilirlik** | 2/10 | **3.5/10** | **Hâlâ tek bir otomatik test yok.** Ama altyapı açıldı: `pom.xml` ile JUnit tek bağımlılık uzakta (`[A6]` kısmi); bloklayan `JOptionPane` modalları gitti (eskiden test "fiilen imkânsız"dı); `Trace` derleme-zamanı toggle → testler debug çıktısına boğulmaz; `getSolutionName()` Person + `PlayGame` testini engelleyen cast'i kaldırdı; davranış tekrarlanabilir metrik koşularıyla (12_400 / 511_816 / …) sabitlendi — fakir adamın regresyon kontrolü. `Scanner(System.in)` hâlâ `Main`/`BuildGame`/`SafeScannerInput`'a gömülü. |
| **Genel Ortalama** | **~4.6/10** | **~6.0/10** | (6.5+7.5+6+6.5+3.5)/5. Temizlik turları okunabilirliği ve kodlama kalitesini belirgin yükseltti; mimari borç ve sıfır test hâlâ tavanı bastırıyor. |

Kısaca: **fikir ve algoritma tarafı iyi, işçilik tarafı — eskiden dağınıktı,
artık büyük ölçüde toparlandı; kalan borç mimari + test + isimlendirme.**
Aşağıdaki maddeler önem sırasına göre değil, kategori bazlı sıralandı;
§5'te öncelik sıralı bir eylem planı, §7'de kriter bazında ne değiştiği var.

---

## 2. Mimari / Tasarım Sorunları

### 2.1 Concrete sınıfa cast ederek soyutlamayı kırma

```java
// src/game/gamerepo/player/robot/solution/second/MathFunctionForSecondSolution.java:39
robot = (Robot) game.getPlayer();
```

```java
// src/game/play/PlayGame.java:137
text += "\nSolution :" + ((Robot) player).getSolution().getClass().getSimpleName();
```

**Neden sorunlu:** `Game.getPlayer()` tipi `Player` (soyut sınıf) ama kod
sürekli `Robot`'a cast ediyor. Bu, `Player` soyutlamasının aslında hiç
soyutlama olmadığını, "Robot" varsayımının kod tabanına sızdığını
gösteriyor. `Person` ile bu satırlar çalışırsa `ClassCastException` fırlar.
`PlayGame.appendFileTotalSolvedValue()` şu an çağrılmıyor olduğu için bu
patlamıyor ama tekrar aktive edilirse (ki siz zaten bu oturumda bir kısmını
aktive ettiniz) Person oynarken çağrılırsa çöker.

**Nasıl düzeltilir:** `Player` sınıfına `getResultFileNamePrefix()` gibi
soyut bir metot ekleyip her iki alt sınıfın kendi implementasyonunu
vermesi (Robot: solution ismini döner, Person: sabit bir isim döner) —
cast'e hiç gerek kalmaz.

### 2.2 Tek sorumluluk (SRP) ihlali — şişkin sınıflar

`MathFunctionForSecondSolution` (219 satır) ve
`MathFunctionWithSpecialFeaturesForThirdSolution` (219 satır) hem
yön hesaplama, hem "ölü nokta" hesaplama, hem navigasyon oluşturma, hem
hafızaya (RoadMemory) yazma sorumluluklarını tek sınıfta topluyor. Örnek:

```java
// src/game/gamerepo/player/robot/solution/second/MathFunctionForSecondSolution.java:48-71
public int calculateFunctionResult() {
    if (isNavigationInRoadMemoryAvailableForThisStep()) {
        navigationService.setCompulsoryLocationToNavigation(game, navigation, lastLocation);
        try {
            selectedDirection = navigationService.getCompulsoryLocation(navigation);
            return selectedDirection.getId();
        } catch (Exception e) {
        }
    }
    calculateForwardAvailableDirectionsOfCurrentDirection();
    double calculationOfDeadlyPoint = calculateDeadlyPoint();
    if (calculationOfDeadlyPoint == CalculationDeadlyPoint.IS_FREE_SO_MOVE_FORWARD) {
        navigation = buildNavigation();
        addNavigationToRoadMemoryList();
    }
    return selectedDirection.getId();
}
```

Bu tek metot; navigasyon önbelleğini okuyor, yön hesaplıyor, "ölü nokta"
hesaplıyor, navigasyon inşa ediyor ve hafızaya yazıyor. Test etmek
isteseniz beşi bir arada test etmeniz gerekir.

**Nasıl düzeltilir:** Bu dört sorumluluğu ayrı, küçük iş birimlerine
(collaborator) bölün — zaten `NavigationService`, `CalculationDeadlyPoint`
gibi yardımcı sınıflar var, `MathFunctionForSecondSolution`'ı bunları
"orkestre eden" ince bir sınıfa indirin, hesaplama mantığının kendisini
oraya taşıyın.

### 2.3 Yön için 8 ayrı sınıf yerine enum

```java
// src/game/location/direction/North.java
public class North extends DirectionLocation {
    @Override
    public int getId() { return getCompass().getNorth(); }
    @Override
    public int getY() { return 3; }   // <-- neden 3? açıklama yok
}
```

`North, South, East, West, NorthEast, NorthWest, SouthEast, SouthWest`
diye 8 ayrı `.java` dosyası var, her biri sadece 1-2 sabit değer
döndürüyor. Ayrıca `DirectionCompassValues.java` de aynı 8 yönü `public
final int` sabitleriyle bir kez daha tanımlıyor — aynı bilgi iki yerde.

**Nasıl düzeltilir:** Java `enum`, tam olarak bu senaryo için var:

```java
public enum Direction {
    NORTH(0, 3), NORTH_EAST(1, ...), EAST(2, ...), /* ... */;
    private final int id;
    private final int y;
    Direction(int id, int y) { this.id = id; this.y = y; }
}
```

8 dosya + 1 sabit sınıfı → tek dosyaya iner, `switch` ile derleyici
tüm case'lerin ele alındığını kontrol eder.

---

## 3. Kodlama Kalitesi / Clean Code Sorunları

### 3.1 Sistematik boolean anti-pattern'i (proje genelinde ~20+ yerde)

```java
// src/validation/Validation.java:14-23
public boolean validateSquareNumbers(int number) {
    final int minimum = 4;
    if (number > minimum) {
        return true;
    }
    return false;
}
```

```java
// src/game/gamerepo/player/robot/solution/second/MathFunctionForSecondSolution.java:170-175
boolean isAvailableWayEqualsToZero(int availableWayNumber) {
    if (availableWayNumber == 0) {
        return true;
    }
    return false;
}
```

```java
// src/game/move/fundamental/secondsolutionforrobot/MoveForwardSecondSolution.java:79-83
boolean isNavigationNull() {
    if (navigation == null)
        return true;
    return false;
}
```

**Neden sorunlu:** `if(cond) return true; return false;` her zaman
`return cond;` ile aynı şeyi yapar ama 4 kat daha uzun. Bu, tek bir yerde
olsa "üslup tercihi" denir ama **proje genelinde 20'den fazla metotta**
tekrarlandığı için bir alışkanlık/anti-pattern halini almış — okuma
hızını düşürüyor, diff'leri şişiriyor.

**Nasıl düzeltilir:** Mekanik bir bul-değiştir: her `if(X) return true;
return false;` → `return X;`. IntelliJ'in kendisi bunu "Simplify boolean
expression" inspection'ıyla otomatik tespit edip düzeltebilir
(Code → Inspect Code, sonra "Simplify" ile toplu uygula).

### 3.2 Boş / anlamsız catch blokları (sessiz hata yutma)

```java
// src/game/gamerepo/player/robot/solution/second/MathFunctionForSecondSolution.java:52-56
try {
    selectedDirection = navigationService.getCompulsoryLocation(navigation);
    return selectedDirection.getId();
} catch (Exception e) {
}
```

**Neden sorunlu:** `Exception` çok geniş bir tip (NPE dahil her şeyi
yakalar) ve catch bloğu tamamen boş — hata olduğunda **hiçbir iz
bırakmadan** yutuluyor. Algoritma neden bazen "yanlış" davrandığını
anlamaya çalışırken bu satır sizi aylarca yanıltabilir.

**Nasıl düzeltilir:** Önce hangi spesifik exception'ın (muhtemelen
`NullPointerException` ya da `IndexOutOfBoundsException`) atıldığını
tespit edin, sadece onu yakalayın; en azından
`ErrorMessage.appearWarnings(getClass(), e.getMessage())` ile loglayın.

### 3.3 Genel `Exception` fırlatma

```java
// src/errormessage/ErrorMessage.java:18-20
public void throwError(Class className, String message) throws Exception {
    throw new Exception("Class where is the WARNING occured : << " + className.getCanonicalName() + ">> Message :" + message);
}
```

**Neden sorunlu:** Çağıran taraf bu hatayı diğer tüm `Exception`
türlerinden ayıramaz; `catch (Exception e)` yazmaya mecbur kalır ki bu da
gerçek programlama hatalarını (NPE gibi) da sessizce yakalamaya iter (bkz.
§2.1, §3.2'deki tüm `catch(Exception)` örnekleri bu tasarımın sonucu).

**Nasıl düzeltilir:** Kendi checked exception sınıfınızı tanımlayın:
`class GameRuleViolationException extends Exception { ... }` gibi —
çağıran taraf artık sadece beklediği hatayı yakalar.

### 3.4 Ölü / yorum satırı haline getirilmiş kod

`PlayGame.java`'da 27, `MathFunctionForSecondSolution.java`'da 18,
`Robot.java`'da 16 satır tamamen yorumlanmış eski kod var. Örnek:

```java
// src/game/gamerepo/player/robot/solution/second/MathFunctionForSecondSolution.java:90-105
Navigation buildNavigation() {
    return navigationService.buildNavigation(game, oneWayNumbersValue, compulsoryLocation);
//        Navigation navigation = new Navigation();
//
//        navigation.setStep(robot.getStep());
//
//        navigation.setOneWayNumbersValue(oneWayNumbersValue);
//
//        if (compulsoryLocation != null) {
//            System.out.println("AAAAAAAAAAAAAAAAAAA step : " + robot.getStep());
//            navigation.setCompulsoryLocation(compulsoryLocation);
//        }
//
//
//        return navigation;
}
```

**Neden sorunlu:** Git zaten eski hali saklıyor (`git log -p`, `git
blame`). Yorum halinde bırakılan kod, okuyan kişiye "bu hâlâ geçerli
olabilir mi?" sorusunu sordurur, dosyayı gereksiz uzatır. Sizin de bu
oturumda "eski mi karışmış" diye kafanız karıştı — işte bu tam olarak o
karışıklığın kaynağı.

**Nasıl düzeltilir:** Sil. Git history zaten orada duruyor, ihtiyaç
olursa `git log -p -- <dosya>` ile geri bakılır.

### 3.5 Yazım hataları (isimlendirme)

`Unknow` (olması gereken: Unknown), `Sqaure` (Square), `ThirdtSolution`
(ThirdSolution), `Stuation`/`Situtaion` (Situation), `Condtion`
(Condition), `Easyly` (Easily) — bunlar `Player.java`, `Move.java`,
`SelectFirstSqaureToStart.java`, `ThirdtSolution_GoldenSquare.java`,
`EasylyReadNumber.java` gibi hem sınıf hem metot isimlerine kadar sızmış.

**Neden sorunlu:** Kritik değil ama IDE'de arama yaparken ("Square" arayıp
"Sqaure" olanı bulamamak gibi) sürtünme yaratıyor, profesyonel izlenimi
zedeliyor.

**Nasıl düzeltilir:** IntelliJ'in "Rename" refactor'üyle (F2) tek tek,
güvenli şekilde düzeltilebilir — tüm referanslar otomatik güncellenir.

### 3.6 Magic number'lar

```java
// src/game/gamerepo/player/robot/solution/second/MathFunctionForSecondSolution.java:155,190
if (oneWayNumbersValue == 2) { compulsoryLocation = location; }
...
boolean isOneWayNumberTooMuchToRunHealtyTheAlgorithm() {
    if (oneWayNumbersValue >= 3) return true;
    return false;
}
```

**Neden sorunlu:** `2` ve `3` sayılarının algoritmik olarak *neden* o
değerler olduğu hiçbir yerde açıklanmıyor. 6 ay sonra siz bile "acaba 3
mü 4 mü olmalıydı" diye tekrar analiz etmek zorunda kalırsınız.

**Nasıl düzeltilir:** `private static final int MAX_ALLOWED_ONE_WAY_COUNT
= 3; // 3'ten fazlası algoritmanın çıkmaza girdiği anlamına gelir` gibi
isimli sabitler + kısa "neden" yorumu.

### 3.7 Public mutable field'lar (encapsulation ihlali)

```java
// src/game/gamerepo/player/robot/solution/BaseSolution.java:12
public Location playerLocation;
```
```java
// src/game/play/PlayGame.java:20
public ComparisonOfSolutions comparisonOfSolutions;
```

**Neden sorunlu:** Bu alanlar dışarıdan doğrudan değiştirilebilir,
sınıfın kendi invariant'larını koruyamaz. `getPlayerLocation()`/
`setPlayerLocation()` ile encapsulate edilmeli.

### 3.8 Loglama yerine `System.out.println` (48 yer) ve `JOptionPane` (27 yer)

```java
// src/errormessage/joptionpanel/ShowPanel.java kullanımına örnek — proje genelinde 27 çağrı
ShowPanel.show(getClass(), " Y siniri asti ");
```

**Neden sorunlu:** Hem konsol çıktısı hem de modal pencere (`JOptionPane`)
debug amaçlı serpiştirilmiş; production/test ortamında `JOptionPane`
programın **beklemeye girmesine** (kullanıcı tıklayana kadar) sebep olur.
Bu da otomatik test yazmayı fiilen imkânsızlaştırıyor.

**Nasıl düzeltilir:** `java.util.logging` (JDK'da hazır, ekstra bağımlılık
gerekmez) ile seviyeli loglama (`FINE`, `WARNING`, `SEVERE`) kullanın;
`ShowPanel`'i sadece gerçekten kullanıcıya bir şey göstermeniz gereken
yerlerde (ör. `Main`'deki hata diyalogları) bırakın.

---

## 4. Test Edilebilirlik / Güvenilirlik

- **Sıfır otomatik test.** `src` altında `*Test*.java` deseni hiçbir
  sonuç vermiyor.
- Bu, önceki oturumda bulduğumuz `Robot.setGame()`/`setSolution()`
  sıralama bug'ının **aylarca fark edilmeden kalmasının** doğrudan
  sebebiydi — hata fırlatmıyordu, sadece yanlış dosya adı üretiyordu.
  Basit bir birim testi (`setGame` sonra `setSolution` çağır, dosya
  adını assert et) bunu anında yakalardı.
- Statik `System.out`/`JOptionPane` yan etkileri ve `new Scanner(System.in)`
  kullanımı (giriş/çıkışın enjekte edilememesi) test yazmayı zorlaştırıyor.

**Nasıl düzeltilir:** Projeyi Maven/Gradle'a taşıyıp (aşağıda §5'te) JUnit
5 ekleyin. En yüksek getiriyi, yan etkisi olmayan saf mantık sınıflarından
alırsınız: `Validation`, `Model`, `BaseSolution.getSolutionFileName()`,
`MathFunctionForSecondSolution` gibi. UI/IO içeren `Main`, `PlayGame` gibi
sınıfları en son test edin.

---

## 5. Öncelik Sıralı Eylem Planı

Kolay/etkisi yüksekten zor/etkisi düşüğe doğru:

1. **Ölü/yorumlanmış kod bloklarını sil** (§3.4) — risk sıfır, okunabilirliği
   anında artırır. Git history zaten koruyor.
2. **Boolean anti-pattern'ini toplu düzelt** (§3.1) — IntelliJ inspection
   ile mekanik, riski düşük.
3. **Yazım hatalarını `Rename` refactor'üyle düzelt** (§3.5).
4. **İlk birkaç birim testini yaz** — `Validation`, `Model` gibi saf
   mantık sınıflarından başlayın. Bu, ileride benzer "setGame sıralaması"
   türü bug'ları erken yakalamanızı sağlar.
5. **Boş/genel catch bloklarını düzelt** (§3.2, §3.3) — önce hangi
   exception'ın gerçekten atıldığını `e.printStackTrace()` ile bir kez
   çalıştırıp görün, sonra spesifik tipe daraltın.
6. **`System.out`/`JOptionPane` yerine `java.util.logging`'e geçin**
   (§3.8) — testleri "sessiz" hâle getirir.
7. **Magic number'ları isimli sabitlere çevirin** (§3.6).
8. **8 yön sınıfını tek bir `enum`'a indirin** (§2.3) — kod tabanını
   belirgin şekilde küçültür.
9. **`(Robot) game.getPlayer()` cast'lerini kaldırın**, `Player`
   soyutlamasını gerçek anlamda polymorphic yapın (§2.1).
10. **Maven/Gradle'a geçiş** — şu an ne `pom.xml` ne `build.gradle` var,
    proje saf IntelliJ modülü olarak yönetiliyor. Bu, bağımlılık
    eklemeyi (ör. JUnit), CI kurmayı ve "tek komutla derle/test et"
    akışını imkânsızlaştırıyor. Küçük bir proje için bile bu geçiş
    ileride büyük kolaylık sağlar.
11. **`MathFunctionForSecondSolution`/`ThirdSolution` gibi şişkin
    sınıfları böl** (§2.2) — en zor/zaman alıcı madde, en sona bırakın.

---

## 6. Olumlu Yönler (adil olmak adına)

- **Algoritmik ilerleme gerçek**: brute-force kombinasyondan
  (`FirstSolution_Combination`) ileri-bakışlı ağırlıklı heuristiğe
  (`SecondSolution_CalculateForwardAvailableWays`) ve graph tabanlı bir
  yaklaşıma (`ThirdtSolution_GoldenSquare`, `Graph`/`Edge`/`Vertex`)
  geçiş, kendi kendine öğrenen biri için gayet iddialı ve tutarlı bir
  ilerleme.
- **Strategy + Template Method** niyeti doğru yerde kullanılmış
  (`Player`→`Person`/`Robot`, `BaseSolution`→üç algoritma, `Move`→
  `MoveForward`/`MoveBack`).
- Çoğu metot **küçük ve tek işe odaklı** isimlendirilmiş
  (`isNavigationInRoadMemoryAvailableForThisStep()` gibi) — uzunluklarına
  rağmen niyet okunabilir; bu, "büyük God-method" tipi projelerden çok
  daha iyi bir başlangıç noktası.
- `.gitignore` artık düzgün, build çıktıları ve üretilmiş dosyalar
  git'ten temizlendi (bu oturumda yapıldı).

---

## 7. Yeniden Değerlendirme — Kriter Bazında Ne Değişti (2026-08-31)

Aşağıdaki tablo §1'deki puanların **neden** oynadığını (ya da oynamadığını)
madde madde açıyor. Referanslar `yapilmasi-gereken-duzeltmeler.md` madde
kodlarına.

| Kriter | Δ | Yapılanlar (puanı yukarı çeken) | Hâlâ açık (tavanı bastıran) |
|---|---|---|---|
| Mimari / Tasarım | +0.5 | `DirectionCompassValues` çift-tanımı silindi; Person `ClassCastException` yolu `Player.getSolutionName()` ile kapandı (`[R1]`); `Model.getTotalSquareCount()` edge² dağınıklığını topladı (`[R2]`); `pom.xml` ile gerçek modül. | 12 `(Robot)` cast (`[A2]`); `MathFunctionForSecondSolution` SRP (`[A3]`); 8 yön sınıfı → enum (`[A1]`); kalıtım istismarı (`[A4]`); `Game`'in kullanılmayan constructor'ı (`[A7]`). |
| Çözüm Yöntemi | 0 | — (bilerek dokunulmadı; her tur 5x5/6x6 metrikleri sabit). | — |
| Kodlama Kalitesi | +2 | Boolean anti-pattern ~22 metotta düzeldi (`[B1]`/`[B2]`); boş `catch` (`[R7]`); `JOptionPane`/`ShowPanel` 27→0, `sleep` paketi silindi (`[L4]`/`[L7]`); `Math.pow` sıcak yoldan çıktı (`[R2]`); `WeightOfAvailableWay` taşma guard'ı (`[R5]`); `[R3]`/`[R4]`/`[R6]`. | ~72 yazım hatası eşleşmesi, 26 dosya (`[N1]`–`[N6]`); magic number'lar (`[M2]`–`[M8]`); genel `Exception` (`[X1]`); `assert` (`[X8]`); `-1` sentinel dönüş (`[X7]`); 4 adet `catch (Exception)`. |
| Clean Code | +3 | Ölü/yorumlu kod büyük ölçüde gitti (`[D1]`–`[D16]`): `PlayGame`/`MathFunctionForSecondSolution`/`Robot` yorum blokları, 2 ölü dosya, ~9 kullanılmayan import, atanıp okunmayan alanlar. `Trace` derleme-zamanı loglama (`[L1]`, javadoc'lu). İzlenebilir remediation planı (`yapilmasi-gereken-duzeltmeler.md`). | Yazım hataları + TR/EN yorum karışıklığı (`[N6]`); bilerek bırakılan `printGamelastStuation` yorumlu blokları; `.editorconfig`/`.gitattributes` yok (`[C1]`/`[C2]`). |
| Test Edilebilirlik | +1.5 | `pom.xml` → JUnit tek bağımlılık uzakta (`[A6]` kısmi); bloklayan modal'lar gitti; `Trace` toggle testleri sessizleştirir; cast kaldırma `PlayGame`'i Person'la test edilebilir yaptı; tekrarlanabilir metrik koşuları (12_400 / 511_816 / 1_023_656 / 83_076) bir regresyon ağı. | **0 test** hâlâ gerçek; `src/test` yok, JUnit eklenmedi (`[A5]`); `Scanner(System.in)` gömülü; sonuç çıktısı statik `System.out`. |

**Net etki:** ~4.6 → ~6.0. Ucuz ve risksiz olan (ölü kod, boolean,
JOptionPane, Trace) yapıldı; pahalı olan (enum refactor, cast temizliği,
SRP bölme, ilk testler) duruyor. Bir sonraki en yüksek getirili adım:
**Bölüm 5 (`[N*]` isimlendirme — IntelliJ `Shift+F6`)** + **`[A5]` ilk
birim testleri** (Maven artık hazır).

---

## Özet

Proje, 2020 seviyesi için **iddialı bir algoritma denemesi** — asıl değer
orada. İlk değerlendirmede en çok puan kaybettiren noktalar (ölü kod,
sistematik boolean anti-pattern'i, `JOptionPane` yan etkileri, dağınık
debug loglama) **bu turlarda büyük ölçüde kapatıldı** — Clean Code ve
Kodlama Kalitesi belirgin yükseldi (§7). Kalan borç daha zor kısımda
yoğunlaşıyor: **mimari** (concrete cast'ler, şişkin sınıflar, 8 yön
sınıfı), **sıfır otomatik test** ve **yaygın yazım hataları**. Maven
geçişi yapıldığı için artık JUnit eklemek ve §5'in kalan maddelerini
işlemek önündeki teknik engel de kalktı.
