# Proje Değerlendirmesi — GameSolutionAlgorithmWithoutFrame

Bu inceleme `src/` altındaki ~90 Java dosyasının tamamının taranmasıyla
hazırlandı (dosya boyutları, tekrar eden kalıplar, yorum/ölü kod oranı,
hata yönetimi, interface kullanımı vb. sistematik olarak tarandı, en
kritik dosyalar tek tek okundu). Amaç: nerede sıkıntı var, neden sıkıntı,
ve nasıl düzeltilir — somut kod örnekleriyle.

---

## 1. Genel Puanlama

| Kriter | Puan (10 üzerinden) | Kısa gerekçe |
|---|---|---|
| **Mimari / Tasarım** | 6/10 | Strategy + Template Method niyeti doğru (Player→Person/Robot, BaseSolution→First/Second/Third), ama katmanlar arası sıkı bağlılık (concrete cast'ler), SRP ihlalleri var. |
| **Çözüm Yöntemi (algoritma)** | 7.5/10 | Üç farklı algoritma (brute-force kombinasyon → ileri-bakışlı heuristik → graph tabanlı "golden square") gerçek bir algoritmik ilerleme gösteriyor. Bu proje için en güçlü yön. |
| **Kodlama Kalitesi** | 4/10 | Yazım hataları, tekrar eden anti-pattern'ler (aşağıda), magic number'lar, tutarsız hata yönetimi. |
| **Clean Code Uygunluğu** | 3.5/10 | Ölü/yorumlanmış kod oranı yüksek, boolean dönüşlerde sistematik bir anti-pattern var (bkz. §3.1), yorumların çoğu "ne"yi değil kafa karışıklığını anlatıyor. |
| **Test Edilebilirlik / Güvenilirlik** | 2/10 | **Projede hiç test yok** (`find . -iname "*test*"` → sıfır sonuç). Statik `System.out` / `JOptionPane` yan etkileri test yazmayı zorlaştırıyor. |
| **Genel Ortalama** | **~4.6/10** | 2020'de tek başına yazılmış, öğrenme amaçlı bir proje için hedef iddialı ve isabetli; ama "temiz kod" standardına göre ciddi bir toparlama geçirmesi lazım. |

Kısaca: **fikir ve algoritma tarafı iyi, işçilik tarafı dağınık.** Aşağıdaki
maddeler önem sırasına göre değil, kategori bazlı sıralandı; §5'te öncelik
sıralı bir eylem planı var.

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

## Özet

Proje, 2020 seviyesi için **iddialı bir algoritma denemesi** — asıl değer
orada. Ama "clean code" ve "sürdürülebilirlik" açısından bugünün
standartlarına göre; ölü kod, sistematik boolean anti-pattern'i, sıfır
test ve tutarsız hata yönetimi en çok puan kaybettiren noktalar. §5'teki
ilk 4 madde (ölü kod temizliği, boolean sadeleştirme, yazım hataları,
ilk birim testleri) bir günden az sürede yapılabilir ve okunabilirliği
orantısız şekilde artırır.
