# Benzersiz Alan Hesabi (Kare Simetrisi Carpanlari)

NxN kare bir tahtada, baslangic karesi olarak secilebilecek 8 farkli simetri
(4 donme + 4 yansima - matematikte "D4 dihedral grubu") altinda ayni sonucu
veren kareler var. `Move.changeStartLocationSpecialMovement()` artik SADECE
"temel bolgeyi" (`0 <= y <= x <= half`, `half = (rowCount-1)/2`, tam sayi
bolme) geziyor - digerlerini bu carpanlarla turetiyoruz.

**Koordinat sistemi:** `(0,0)` sol-alt, x sağa, y yukari artiyor (projedeki
`bash/latest-grid.sh` ve sohbetlerdeki grid gosterimleriyle ayni).

## Genel kural

Bir `(x, y)` karesinin carpani, o karenin kac FARKLI karaya (kendisi dahil)
karsilik geldigi - yani "orbit" buyuklugu:

- **N TEK sayi** (`half = (N-1)/2` tahtanin TAM merkez satir/sutunu):
  - `x == half AND y == half` (tam merkez) → **x1**
  - `x == y` (kosegen, merkez degil) VEYA `x == half` (merkez dikey/yatay eksen, merkez degil) → **x4**
  - digerleri → **x8**
- **N CIFT sayi** (`half = N/2 - 1`, tam bir merkez HUCRE yok - merkez iki hucre arasinda):
  - `x == y` (kosegen) → **x4**
  - digerleri → **x8**
  - (`x == half` tek basina CIFT N'de carpani DUSURMEZ - cunku o eksen tam bir hucreye denk gelmiyor)

**Dogrulama (5x5, gercek veriyle):**
`(0,0)=552, (1,0)=548, (2,0)=552, (1,1)=412, (2,1)=400, (2,2)=352`

```
552×4 + 548×8 + 552×4 + 412×4 + 400×4 + 352×1
= 2208 + 4384 + 2208 + 1648 + 1600 + 352
= 12400   ✓ (bilinen gercek 5x5 toplami ile birebir ayni)
```

## 5x5 (half = 2)

| # | (x,y) | Carpan | Aciklama |
|---|-------|--------|----------|
| 1 | (0,0) | **x4** | kosegen (kose) |
| 2 | (1,0) | **x8** | genel konum |
| 3 | (2,0) | **x4** | x=half (orta dikey/yatay eksen) |
| 4 | (1,1) | **x4** | kosegen |
| 5 | (2,1) | **x4** | x=half |
| 6 | (2,2) | **x1** | TAM MERKEZ |

Kontrol: 4+8+4+4+4+1 = **25** = 5²

## 6x6 (half = 2, N cift → merkez hucre yok)

| # | (x,y) | Carpan | Aciklama |
|---|-------|--------|----------|
| 1 | (0,0) | **x4** | kosegen (kose) |
| 2 | (1,0) | **x8** | genel konum |
| 3 | (2,0) | **x8** | genel konum (N cift, x=half onemsiz) |
| 4 | (1,1) | **x4** | kosegen |
| 5 | (2,1) | **x8** | genel konum |
| 6 | (2,2) | **x4** | kosegen |

Kontrol: 4+8+8+4+8+4 = **36** = 6²

## 7x7 (half = 3)

Harita: ayni harfli kareler ayni sonucu verir (bir harfin tahtada kac kez
gectigi = o harfin carpani). `(0,0)` sol-alt, x saga, y yukari.

```
y=6 │ A  B  C  D  C  B  A
y=5 │ B  E  F  G  F  E  B
y=4 │ C  F  H  I  H  F  C
y=3 │ D  G  I  J  I  G  D
y=2 │ C  F  H  I  H  F  C
y=1 │ B  E  F  G  F  E  B
y=0 │ A  B  C  D  C  B  A
    └─────────────────────
      0  1  2  3  4  5  6   (x)
```

| # | Harf | (x,y) | Carpan | Aciklama |
|---|------|-------|--------|----------|
| 1  | A | (0,0) | **x4** | kosegen (kose) |
| 2  | B | (1,0) | **x8** | genel konum |
| 3  | C | (2,0) | **x8** | genel konum |
| 4  | D | (3,0) | **x4** | x=half |
| 5  | E | (1,1) | **x4** | kosegen |
| 6  | F | (2,1) | **x8** | genel konum |
| 7  | G | (3,1) | **x4** | x=half |
| 8  | H | (2,2) | **x4** | kosegen |
| 9  | I | (3,2) | **x4** | x=half |
| 10 | J | (3,3) | **x1** | TAM MERKEZ |

Kontrol: 4+8+8+4+4+8+4+4+4+1 = **49** = 7²

**Gercek sonuc (7x7, Algoritma 2):**
kaynak `rapor/FileTotalScoreCount/Solution-2-7x7_EverySingleSquareTotalValue.txt`

```
A (0,0) = 468_698_008 × 4 = 1_874_792_032
B (1,0) = 233_127_829 × 8 = 1_865_022_632
C (2,0) =  30_197_874 × 8 =   241_582_992
D (3,0) =  45_107_348 × 4 =   180_429_392
E (1,1) = 114_606_142 × 4 =   458_424_568
F (2,1) = 282_218_332 × 8 = 2_257_746_656
G (3,1) =   6_409_586 × 4 =    25_638_344
H (2,2) =  14_066_652 × 4 =    56_266_608
I (3,2) =  59_238_546 × 4 =   236_954_184
J (3,3) = 125_178_016 × 1 =   125_178_016
-------------------------------------------
Wedge toplami (10 kare)  = 1_378_848_333
7x7 TOPLAM               = 7_322_035_424
```

## 8x8 (half = 3, N cift → merkez hucre yok)

| # | (x,y) | Carpan | Aciklama |
|---|-------|--------|----------|
| 1  | (0,0) | **x4** | kosegen (kose) |
| 2  | (1,0) | **x8** | genel konum |
| 3  | (2,0) | **x8** | genel konum |
| 4  | (3,0) | **x8** | genel konum (N cift, x=half onemsiz) |
| 5  | (1,1) | **x4** | kosegen |
| 6  | (2,1) | **x8** | genel konum |
| 7  | (3,1) | **x8** | genel konum |
| 8  | (2,2) | **x4** | kosegen |
| 9  | (3,2) | **x8** | genel konum |
| 10 | (3,3) | **x4** | kosegen (N cift, merkez degil) |

Kontrol: 4+8+8+8+4+8+8+4+8+4 = **64** = 8²

## 9x9 (half = 4)

| # | (x,y) | Carpan | Aciklama |
|---|-------|--------|----------|
| 1  | (0,0) | **x4** | kosegen (kose) |
| 2  | (1,0) | **x8** | genel konum |
| 3  | (2,0) | **x8** | genel konum |
| 4  | (3,0) | **x8** | genel konum |
| 5  | (4,0) | **x4** | x=half |
| 6  | (1,1) | **x4** | kosegen |
| 7  | (2,1) | **x8** | genel konum |
| 8  | (3,1) | **x8** | genel konum |
| 9  | (4,1) | **x4** | x=half |
| 10 | (2,2) | **x4** | kosegen |
| 11 | (3,2) | **x8** | genel konum |
| 12 | (4,2) | **x4** | x=half |
| 13 | (3,3) | **x4** | kosegen |
| 14 | (4,3) | **x4** | x=half |
| 15 | (4,4) | **x1** | TAM MERKEZ |

Kontrol: 4+8+8+8+4+4+8+8+4+4+8+4+4+4+1 = **81** = 9²

## 10x10 (half = 4, N cift → merkez hucre yok)

| # | (x,y) | Carpan | Aciklama |
|---|-------|--------|----------|
| 1  | (0,0) | **x4** | kosegen (kose) |
| 2  | (1,0) | **x8** | genel konum |
| 3  | (2,0) | **x8** | genel konum |
| 4  | (3,0) | **x8** | genel konum |
| 5  | (4,0) | **x8** | genel konum (N cift, x=half onemsiz) |
| 6  | (1,1) | **x4** | kosegen |
| 7  | (2,1) | **x8** | genel konum |
| 8  | (3,1) | **x8** | genel konum |
| 9  | (4,1) | **x8** | genel konum |
| 10 | (2,2) | **x4** | kosegen |
| 11 | (3,2) | **x8** | genel konum |
| 12 | (4,2) | **x8** | genel konum |
| 13 | (3,3) | **x4** | kosegen |
| 14 | (4,3) | **x8** | genel konum |
| 15 | (4,4) | **x4** | kosegen (N cift, merkez degil) |

Kontrol: 4+8+8+8+8+4+8+8+8+4+8+8+4+8+4 = **100** = 10²

## Toplam sonucu hesaplama

```
Toplam_NxN = Sum( wedge_hucresi_cozum_sayisi × o_hucrenin_carpani )
```

Yukaridaki her tablodaki kontrol satiri (carpanlarin toplami = N²) dogru
sayildigini garanti eder - eger bir gun baska bir N icin bu tabloyu
genisletirsen, carpanlar toplaminin mutlaka N²'ye esit olmasi gerektigini
unutma (aksi halde bir hucre eksik/fazla sayilmis demektir).
