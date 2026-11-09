# Detaylı Çözüm 1 — `FirstSolution_Combination`

Algoritma 1, budama yapmayan saf geri izlemedir (DFS). Her adımda sıradaki boş yönü dener, yön kalmayınca geri döner.
Algoritma 2 aynı oyun döngüsünü ve aynı hareket altyapısını kullanır. Bu yüzden ortak altyapı burada anlatılır, `detayli-cozum-2.md` sadece farkları ekler.

Bu dosyadaki sözde kod ayrı bir Java programına çevrilip çalıştırıldı. 5x5 ALL taramasında dört sayaç da `GameRegressionTest` değerleriyle birebir tuttu:
çözüm 12.400, round 4.809.736, dummy back 603.928, total back 2.404.856.

---

## 1. Oyun kuralları

- Tahta `N x N`. Koordinat `(x, y)`, `(0,0)` sol alt köşe, `x` sağa, `y` yukarı artar.
- Oyuncu bir kareden başlar ve her kareye tam bir kez basar.
- `step == N²` olunca tahta dolmuştur ve bu bir çözümdür. Başlangıca dönme şartı yoktur.
- Matematiksel karşılığı: 8 yönlü özel hamle grafında **Hamilton yolu sayma**.

Hamleler `Location` yön sınıflarından gelir (`src/game/location/direction/`). Sıra `LocationsList.fillList` ile sabittir:

| id | Yön | dx | dy | Tür |
|---:|---|---:|---:|---|
| 0 | North | 0 | +3 | düz, 2 kare atlar |
| 1 | NorthEast | +2 | +2 | çapraz, 1 kare atlar |
| 2 | East | +3 | 0 | düz |
| 3 | SouthEast | +2 | -2 | çapraz |
| 4 | South | 0 | -3 | düz |
| 5 | SouthWest | -2 | -2 | çapraz |
| 6 | West | -3 | 0 | düz |
| 7 | NorthWest | -2 | +2 | çapraz |
| 8 | LastLocation | — | — | "geri dön" komutu |

`sozde-kod.md` yönleri komşu kare gibi anlatıyor. Gerçekte hamleler 3 düz veya 2 çapraz atlamadır. Bir kareye gelebileceğin kareler ile o kareden gidebileceğin kareler aynıdır, yani graf yönsüzdür. Algoritma 2'nin matematiği buna dayanır.

---

## 2. Durum (state)

| Alan | Yer | Anlam |
|---|---|---|
| `gameSquares[x][y]` | `Model` | O kareye kaçıncı adımda basıldı. 0 = boş. |
| `visitedAreas[x][y]` | `Model` | Kare şu anki yolda mı. |
| `visitedDirections[step][dir]` | `Player` | Bu adımda bu yön denendi mi. Geri izlemenin "kaldığım yer" hafızası budur. |
| `step` | `Player` | Yoldaki kare sayısı. Başlangıçta 1, çözümde N². |
| `location` | `Player` | Oyuncunun konumu. |
| `roundCounter` | `Game` | `while` döngüsü tur sayısı. |
| `totalGameFinishedScore` | `Score` | Bulunan toplam çözüm. |
| `squareTotalSolvedValue` | `Player` | O başlangıç karesindeki çözüm sayısı. Kare değişince sıfırlanır. |
| `counterTotalBackStep` | `Score` | Geri adım sayısı. Başlangıç karesi değişimi sayılmaz. |
| `counterOfDummyBackMove` | `Score` | Çözüm bulunmadan başlayan geri çekilme serisi sayısı, yani çıkmaz sayısı. |

---

## 3. Ana döngü — `PlayGame.playGame`

```text
başlat: başlangıç (0,0), step = 1, kareyi işaretle, gameSquares[0][0] = 1

DÖNGÜ oyun bitmediyse:
    roundCounter += 1
    yön = solution.getLocationInput()                      // algoritmaya özel tek yer
    hareket = (yön == GERİ) ? moveBack : moveForward
    hedef = yönün vektörü                                  // GERİ ise aşağıdaki not
    hareket.move(hedef)
    EĞER step == N²:
        toplamÇözüm += 1
        kareÇözümü  += 1
        solutionIndex += 1
        checkpoint yazmayı dene
```

GERİ için vektör `CreateLocationOfLastStep` ile bulunur. Tahta baştan sona taranır, `step-1` değerli kare bulunur, fark alınır. Yani önceki kare yığında tutulmaz, her geri adımda `O(N²)` tarama yapılır.

---

## 4. Algoritma 1'in kendisi — `getLocationInput`

```text
FONKSİYON getLocationInput():
    konum = oyuncu konumu
    HER yön, 0..7 sırasıyla:
        EĞER sınırlar içinde
           VE hedef kare ziyaret edilmemiş
           VE visitedDirections[step][yön] == false:
            DÖNDÜR yön
    DÖNDÜR GERİ
```

Kontrol `SquareProcess.isSquareAvailableToMoveOnIt` içindedir. `step == N²` iken tüm kareler dolu olduğu için ilk koşul false olur ve `visitedDirections[N²]` hiç okunmaz. Dizi boyu N² olduğundan bu okuma yapılsaydı taşma olurdu.

---

## 5. Hareket — `Move.move`

```text
FONKSİYON move(hedef):
    prepareAllStuff()                  // Algoritma 1'de boş

    EĞER step == 1 VE hareket MoveBack ise:
        changeStartLocationSpecialMovement()
    DEĞİLSE:
        updateBeforeStep()
        step değerini güncelle
        updateAfterStep()
        gameSquares[konum] = step

    EĞER oyun bitti: kare sonucunu dosyaya yaz
```

**İleri** (`MoveForward`):

```text
updateBeforeStep: boş
step += 1
updateAfterStep:
    konum += yön
    visitedAreas[konum] = true
    dummy kilidini kapat
    visitedDirections[step - 1][yön] = true      // yeni adım değil, ayrıldığımız adım işaretlenir
gameSquares[konum] = step
```

**Geri** (`MoveBack`):

```text
updateBeforeStep:
    visitedAreas[konum] = false
    EĞER step == N²: dummy kilidini aç           // çözümden dönüş dummy sayılmaz
    EĞER dummy kilitli: dummy += 1, kilidi aç
    EĞER step < N²: visitedDirections[step][*] = false     // terk edilen adımın hafızası silinir
    gameSquares içinde step değerli kareyi 0 yap
step -= 1, totalBack += 1
updateAfterStep:
    konum += önceki kareye vektör
gameSquares[konum] = step                        // zaten aynı değer
```

`visitedDirections` silme işi kritiktir. Bir adıma tekrar farklı yoldan gelindiğinde tüm yönler yeniden denenebilir olmalıdır. `visitedDirections[step-1]` ise korunur, çünkü dönüldüğünde o adımda sıradaki yöne geçilecektir.

---

## 6. Başlangıç karesi değişimi

`step == 1` iken gelen GERİ, geri adım değildir. O karenin tüm ağacı bitmiştir.

```text
FONKSİYON changeStartLocationSpecialMovement():
    kare sonucunu dosyaya yaz, kareÇözümü = 0
    (x, y) = konum
    x += 1
    EĞER tarama ALL:
        EĞER x >= N: x = 0, y += 1
        varMı = (y < N)
    DEĞİLSE (UNIQUE):
        half = (N - 1) / 2
        EĞER x > half: y += 1, x = y
        varMı = (y <= half)
    EĞER varMı:
        gameSquares, visitedAreas, visitedDirections yenile
        step = 1, konum = (x, y), kareyi işaretle
```

UNIQUE sırası: `(0,0), (1,0) ... (half,0), (1,1), (2,1) ... (half,half)`. Simetri çarpanları `unique-area-calculation.md` dosyasındadır.

---

## 7. Oyunun bitişi — `RobotGameOver.isGameOver`

```text
son başlangıç karesindeyiz                  // ALL: (N-1, N-1), UNIQUE: (half, half)
VE step == 1
VE sınır içindeki 8 yönün hepsi visitedDirections[1] içinde işaretli
```

Kontrol döngü başında ve her `move` sonunda çalışır. Son karede GERİ komutu verilmeden oyun biter.

---

## 8. Adım adım örnek (5x5, başlangıç (0,0))

Her satır bir round. Çıktı doğrulama programından alındı.

| Round | step | Konum | Seçilen yön | Neden |
|---:|---:|---|---|---|
| 1 | 1 | (0,0) | N | İlk uygun yön |
| 2 | 2 | (0,3) | E | N ve NE tahta dışı |
| 3 | 3 | (3,3) | S | N, NE, E, SE tahta dışı |
| 4 | 4 | (3,0) | NW | N dolu, W dolu, diğerleri tahta dışı |
| 5 | 5 | (1,2) | NE | N tahta dışı |
| 6 | 6 | (3,4) | S | N, NE, E, SE tahta dışı |
| 7 | 7 | (3,1) | W | Önceki yönler dışarıda veya dolu |
| 8 | 8 | (0,1) | N | İlk uygun yön |

Yön kalmayan karede GERİ gelir. `visitedDirections[o adım]` silinir, bir önceki adımda işaretli olmayan sıradaki yön denenir.

---

## 9. Maliyet ve sayaç bağıntısı

- Yön seçimi en fazla 8 kontrol yapar.
- Her geri adımda iki tahta taraması vardır: `calculateIndexOfGivenStepInGameSquareArrays` ve `deleteMaxStep`.
- Her ileri adım en sonunda bir geri adımla kapanır. Bu yüzden şu bağıntı tam tutar:

```text
round = 2 × totalBack + başlangıç karesi değişim sayısı
5x5 ALL: 2 × 2.404.856 + 24 = 4.809.736
```

Algoritma 1 çıkmazı ancak içine girip yön bitince fark eder. Algoritma 2'nin kazancı bu noktadan gelir: 5x5 ALL'da round sayısı 4.809.736'dan 1.023.656'ya iner.
