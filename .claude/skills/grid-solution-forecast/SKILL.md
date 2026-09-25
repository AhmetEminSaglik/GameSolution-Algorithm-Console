---
name: grid-solution-forecast
description: >
  Bir sonraki NxN grid (orn. 8x8, 9x9, 10x10) tamamlanmadan once, daha kucuk
  boyutlardaki (5x5, 6x6, 7x7, ...) tamamlanmis/kismi cozum verilerinden yola
  cikarak buyume oranini modelleyip toplam cozum sayisini ve hucre bazli oran
  tablosunu tahmin eder. Use for "8x8 icin tahmini sonuc", "buyume orani",
  "ekstrapolasyon", "9x9/10x10 tahmini", "5-6-7'den 8'e" gibi isteklerde.
---

# Grid cozum sayisi ekstrapolasyonu (buyume orani modeli)

Bu proje NxN gridlerde (Algoritma 2 - `SecondSolution_CalculateForwardAvailableWays`)
tum cozumleri sayiyor. Buyuk boyutlar (8x8, 9x9, 10x10, ...) gunler surebiliyor.
Bu skill, KUCUK/TAMAMLANMIS boyutlardaki gercek verilerden, henuz calisilmamis/
yarim kalmis bir sonraki boyut icin **yaklasik** bir toplam ve hucre-bazli oran
tablosu cikarir - kesin sonuc degildir, sadece "ne kadar surer / kabaca kac
cozum olur" sorusuna kaba bir cevaptir.

**Onceki calisma (7x7 icin):** `tahmini-7x7-sonucu.txt` (proje koku) - bu
skill'in TAM ornegi, ayni formatta yeni boyutlar icin de dosya uretilecek.

## 1. Simetri / carpan kurali (her N icin gecerli)

NxN karede D4 dihedral simetri (4 donme + 4 yansima = 8 esdeger donusum)
altinda ayni sonucu veren kareler var. Sadece "temel bolgeyi"
(`0 <= y <= x <= half`, `half = (rowCount-1)/2` tam sayi bolme) gezmek yeterli;
digerleri carpanla turetiliyor. Detay + 8x8/9x9/10x10 hazir tablolar:
`unique-area-calculation.md` (proje koku) - HER ZAMAN bu dosyadan oku, tekrar
turetme (satir sayisi N buyudukce artiyor, elle hata riski var).

Genel kural (ozet):
- **N TEK**: `x==y==half` (tam merkez) -> **x1**; `x==y` (kosegen, merkez
  degil) VEYA `x==half` (merkez eksen, merkez degil) -> **x4**; digerleri -> **x8**
- **N CIFT** (merkez hucre yok): `x==y` (kosegen) -> **x4**; digerleri -> **x8**

Kontrol: bir N icin tum carpanlarin toplami HER ZAMAN N² olmali - `unique-area-
calculation.md`'deki her tablonun altinda bu kontrol satiri var, yeni N
eklerken de mutlaka ekle.

## 2. Veri kaynaklari

- `rapor/FileTotalScoreCount/Solution-2-<N>x<N>_EverySingleSquareTotalValue.txt`
  - `[x][y] = deger` formatinda, o hucreden BASLAYAN toplam cozum sayisi
    (Algoritma 2, "her tekil kareye gore toplam" ozelligi - `BuildGame`'in
    simetri dongusu tamamlandikca hucre hucre yaziliyor).
  - TAMAMLANMIS bir boyutta: butun `[x][y]` cifleri var (bazen ayni blok
    birden fazla kez tekrar ediyor - 5x5 dosyasinda oldugu gibi; dedupe et,
    ilk gorulen deger yeterli, cunku deterministik ayni sonucu verir).
  - DEVAM EDEN bir boyutta: sadece o ana kadar TAMAMEN bitmis wedge
    hucreleri var (wedge, `BuildGame`'in simetri dongu sirasina gore
    dolduruluyor - genelde y=0 satiri once). Eksik satirlar o hucrenin
    HENUZ bitmedigini gosterir, hata degildir.
- `rapor/RunStatistic/run-statistic-<N>-<N>.txt` ve/veya `solving_checkpoint`
  DB'deki en son `total_solved` - TAMAMLANMIS bir boyutun GERCEK toplamini
  dogrulamak icin (hucre-bazli carpan toplamiyla birebir eslesmeli - eslesmezse
  hesap veya veri hatasi var demektir, once onu duzelt).

## 3. Adimlar (N_bilinenler -> N_hedef, orn. [5,6,7] -> 8)

1. **Her bilinen N icin gercek toplami dogrula.** Wedge hucrelerini oku,
   `unique-area-calculation.md`'deki carpanla carp, topla. Bilinen gercek
   toplamla (DB veya run-statistic dosyasi) KARSILASTIR - eslesmiyorsa DUR,
   once veri/hesap hatasini bul (bkz. 6x6/5x5 icin daha once yapilan
   dogrulama - ikisi de birebir eslesmisti).
2. **Buyume orani fit et - TUM bilinen noktalari kullan, sadece son ikisini
   degil.** `ln(Toplam)` ile `kare_sayisi (N²)` arasinda DOGRUSAL bir iliski
   varsay (`Toplam(N) = A * buyume^(N²)`), TUM bilinen (N², Toplam) ciftleriyle
   en kucuk kareler (least squares) dogru fit et. 2 nokta varsa (5,6) bu
   basit iki-nokta egimine esittir (`buyume = (T6/T5)^(1/(36-25))`); 3+ nokta
   olunca (5,6,7) regresyon daha guvenilir olur - SADECE son iki noktayi
   (6,7) kullanmak yerine 5'i de dahil et, cunku tek bir gecisin gurultusune
   (bkz. asagidaki uyari) daha az duyarli olur.
3. **Toplami ekstrapole et:** `Toplam(N_hedef) = A * buyume^(N_hedef²)`.
4. **Hucre-bazli (asagidan-yukari) capraz kontrol yap:**
   - Hedef boyutta GERCEK olarak bilinen wedge hucreleri varsa (kismi calisma
     - orn. 7x7'de y=0 satiri) DOGRUDAN kullan, tahmin etme.
   - Eksik hucreler icin: bir onceki N'nin AYNI koordinatli hucresine (varsa)
     genel buyume carpanini (`Toplam(N_hedef)/Toplam(N_onceki)`) uygula.
   - Onceki N'de KARSILIGI olmayan hucreler (orn. N buyudukce yeni ortaya
     cikan `x=yeni_half` sutunu) icin: hedef N'nin KENDI bilinen satirindaki
     komsu-oran kalibini (orn. `x=half / x=half-1` orani) bir onceki satira/
     hucreye uygula (zincirleme, ZAYIF guven - acikca etiketle).
   - Bu iki taban toplami (adim 3'teki ustel fit) ile CAPRAZ KONTROL et - ikisi
     ayni buyuklukte (mertebe farki yoksa) sonuc tutarli demektir.
5. **Sonucu kaydet:** `tahmini-<N>x<N>-sonucu.txt` (proje koku, `tahmini-7x7-
   sonucu.txt` ile AYNI format): buyume modeli + formul, iki yontemin sonucu,
   oran tablosu (`x` = (0,0) hucresi, digerleri onun kati, virgulden sonra 2
   hane), her hucre icin GUVEN etiketi (GERCEK / tahmin-orta / tahmin-zayif),
   ve son bir uyari paragrafi.

## 4. Onemli uyarilar (7x7 tahmininden ogrenilen)

- **Hucre-bazli buyume orani grid boyutuna gore SABIT DEGIL.** 7x7'de
  `(1,0)/(0,0)` orani (0,50x) 5x5 (0,99x) ve 6x6'dan (0,94x) COK dusuk cikti.
  Yani "onceki gecisin oranini oldugu gibi bir sonraki hucreye uygula" hep
  guvenilir degil - bu yuzden adim 4'teki tahminleri MUTLAKA zayif/orta diye
  etiketle, kesinmis gibi sunma.
  - **8x8 tahmini yaparken:** 7x7 TAMAMLANINCA, 5x5/6x6/7x7'nin UCU de
    kullanilabilir olacak - bu durumda SADECE en yakin gecisin (6x6->7x7)
    oranini degil, TUM ucunun regresyonunu kullan (adim 2), cunku tek bir
    gecis (ozellikle (1,0) gibi anomali gosteren hucreler icin) yaniltici
    olabilir.
- **Toplam bazli (yukaridan-asagi) ve hucre bazli (asagidan-yukari) yontem
  bagimsiz calismali** - ikisi ayni mertebede cikarsa tahmine biraz daha
  guven, farkli mertebede cikarsa (10x fark gibi) bir seyler yanlis, tekrar
  kontrol et.
- Eski proje notlarindaki (orn. `PERSISTENCE.md`'deki "7x7 ~2 milyar" gibi)
  kaba tahminler veri OLMADAN yazilmis - yeni ekstrapolasyon bunlardan cok
  farkliysa (7x7'de ~8-9 kat fark cikti) eski notu GUNCELLE, celiskiyi
  sessizce birakma.

## 5. Ornek: 7x7 tahmini (referans - tekrar uretmek icin degil, format ornegi)

`tahmini-7x7-sonucu.txt` dosyasina bak - 5x5+6x6 (2 nokta) ile fit edilen
buyume orani (1,8057/kare), 7x7'nin GERCEK bilinen 4 wedge hucresiyle capraz
kontrol edildi (805.100.000 ilerleme / ~15-18 milyar tahmini = ~%4-5, tutarli
- "cok erken asamada" sonucuna varildi). 8x8 icin ayni format, ama artik 3
nokta (5,6,7) ile regresyon + 7x7'nin (o zaman tam bilinen) TUM wedge
hucreleriyle 8x8'in KISMI verisini capraz kontrol ederek yap.
