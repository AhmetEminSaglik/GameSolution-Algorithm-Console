# Örnek Veri ve Kullanım

Aşağıdaki çıktılar 5x5, 2. çözüm (`SecondSolution`) koşusundan alındı
(`java -jar target/game-solution-algorithm.jar --save=both`, sonra 5 → 2 → 2).

> `solver_run_id = 10` / `run_id = 11` bu makinedeki koşu id'leri. Sende farklı
> olur; genel sorgular için sondaki "id bilmeden" bölümüne bak.

---

## 1) FLAT tablo — `path_explorer_solution`

Her çözüm = 1 satır. `path` = yön-kodlaması (adım başına 3 bit, `PathCodec`).

```bash
docker exec dev-postgres psql -U pathexplorer -d pathexplorer -c "
SELECT id, solution_index AS sol_idx, start_x AS sx, start_y AS sy, path_len AS len,
       open1, open2, open3, encode(path,'hex') AS path_hex
FROM path_explorer_solution
WHERE solver_run_id = 10
ORDER BY id LIMIT 50"
```

```
  id   | sol_idx | sx | sy | len | open1 | open2 | open3 |      path_hex
-------+---------+----+----+-----+-------+-------+-------+--------------------
 49601 |       1 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733aa1deaafe1634
 49602 |       2 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733aa1deaafe1782
 49603 |       3 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733aa1deaae745e8
 49604 |       4 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733c399f0cfe1634
 49605 |       5 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733c399f0cfe1782
 49606 |       6 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733c399f0ce745e8
 49607 |       7 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733c42877af10be1
 49608 |       8 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733c42877af17a19
 49609 |       9 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733c42877aeaf85e
 49610 |      10 |  0 |  0 |  25 |     0 |     3 |    18 | 0a733c4287450ccc1e
 ...  (50 satır) ...
 49649 |      49 |  0 |  0 |  25 |     0 |     3 |    18 | 0a754543333078c3a2
 49650 |      50 |  0 |  0 |  25 |     0 |     3 |    18 | 0a7545433782a2aea6
```

**Sütunlar**

| sütun | anlam |
|---|---|
| `id` | satır id (DB atar) |
| `sol_idx` (`solution_index`) | koşu içinde kaçıncı bulundu |
| `sx, sy` (`start_x/y`) | 1. adımın karesi (başlangıç) |
| `len` (`path_len`) | adım sayısı — tam çözümde `rows*cols` (5x5 → 25) |
| `open1/2/3` | ilk 3 adımın **hücre indeksi** = `x*col_size + y`. İndexli → "şu açılıştan kaç çözüm" sorgusu için |
| `path_hex` | `path BYTEA`'nın hex hali. 25 adım × 3 bit = 72 bit = **9 byte = 18 hex** |

**Okunacak:** İlk 50 çözümün hepsi başlangıç `(0,0)`, `open1=0` (0×5+0),
`open2=3` → adım 2 = `(0,3)`, `open3=18` → adım 3 = `(3,3)` (3×5+3). Yani bu 50
çözüm aynı `(0,0)→(0,3)→(3,3)` açılışını paylaşıyor; sadece devamı (path_hex)
farklı.

---

## 2) TRIE tablo — `solution_step`

Parent-child ağaç. Ortak önek 1 kez. Bir düğüm = kısmi bir yol adımı.

```bash
docker exec dev-postgres psql -U pathexplorer -d pathexplorer -c "
SELECT id, parent_step_id AS parent, step_no AS step, x, y,
       move_from_parent AS mv, solution_ordinal AS ord,
       subtree_solution_count AS subtree_cnt, is_leaf
FROM solution_step
WHERE run_id = 11
ORDER BY id LIMIT 50"
```

```
 id  | parent | step | x | y | mv | ord | subtree_cnt | is_leaf
-----+--------+------+---+---+----+-----+-------------+---------
   1 |        |    1 | 0 | 0 |    |   1 |         552 | f     <- KÖK: (0,0)'dan tüm çözümler
   2 |      1 |    2 | 0 | 3 |  0 |   1 |         248 | f     <- (0,3), mv=0 (Kuzey)
   3 |      2 |    3 | 3 | 3 |  2 |   1 |          90 | f     <- (3,3), mv=2 (Doğu)
   4 |      3 |    4 | 3 | 0 |  4 |   1 |          78 | f
   5 |      4 |    5 | 1 | 2 |  7 |   1 |          78 | f
   6 |      5 |    6 | 3 | 4 |  1 |   1 |          34 | f
   7 |      6 |    7 | 3 | 1 |  4 |   1 |          11 | f
 216 |      7 |    8 | 1 | 3 |  7 |   1 |          11 | f     <- id atladı (7->216): budanan çıkmaz dallar
 ...
 231 |    230 |   23 | 4 | 4 |  0 |   1 |           1 | f
 232 |    231 |   24 | 1 | 4 |  6 |   1 |           1 | f
 233 |    232 |   25 | 1 | 1 |  4 |   1 |           1 | t     <- 1. TAM ÇÖZÜM (is_leaf=t, ord=1)
 234 |    230 |   23 | 1 | 1 |  6 |   2 |           1 | f     <- backtrack; yeni dal -> ord=2
 235 |    234 |   24 | 1 | 4 |  0 |   2 |           1 | f
 236 |    235 |   25 | 4 | 4 |  2 |   2 |           1 | t     <- 2. çözüm
 237 |    226 |   19 | 4 | 4 |  1 |   3 |           1 | f     <- ord=3
 ...
 243 |    242 |   25 | 0 | 4 |  0 |   3 |           1 | t     <- 3. çözüm
 306 |    216 |    9 | 1 | 0 |  4 |   4 |           8 | f     <- ord=4, subtree_cnt=8 (bu daldan 8 çözüm)
 ...
 337 |    336 |   23 | 4 | 4 |  0 |   4 |           1 | f
(50 satır)
```

**Sütunlar**

| sütun | anlam |
|---|---|
| `id` | düğüm id — **client atar** (parent id çocuktan önce lazım). Atlamalar = budanan çıkmaz dallar |
| `parent` (`parent_step_id`) | üst düğüm id (kök için boş) |
| `step` (`step_no`) | derinlik / adım no (1 = kök = başlangıç karesi) |
| `x, y` | bu adımda bulunulan kare |
| `mv` (`move_from_parent`) | parent'tan buraya gelen yön 0-7. Sıra: `0=N 1=NE 2=E 3=SE 4=S 5=SW 6=W 7=NW` (`PathCodec.DIRS`) |
| `ord` (`solution_ordinal`) | **"index"** — düğüm oluşturulduğunda `bulunan_çözüm + 1`. Çözüm bulunana kadar sabit, bulununca +1 |
| `subtree_cnt` (`subtree_solution_count`) | **buradan geçen çözüm sayısı**. "Şu açılıştan kaç çözüm" = bu sütunu OKU, sayma yok |
| `is_leaf` | `step = rows*cols` → tam çözüm (`t`) |

**Okunacak kalıplar**

- `id=1` kök: `(0,0)`, `subtree_cnt=552` → `(0,0)`'dan çıkan tüm çözümler.
- `1→2→3→…` zinciri: `subtree_cnt` derinleştikçe düşüyor (552 → 248 → 90 → … → 1).
- `id=1..233` hepsi `ord=1` → 1. çözüm aranırken oluşturuldu.
- `id=233` `is_leaf=t` → 1. çözüm. Sonra `id=234` `ord=2`, `id=237` `ord=3`,
  `id=306` `ord=4` … — her tam çözümde `ord` +1.
- `id` atlamaları (7→216, 243→306, 308→326) = oluşturulup çözüm çıkmayan
  (budanan) dallar. Saklanmaz.

---

## 3) Koşu id'sini bilmeden

```sql
-- en son flat koşusundan 50 çözüm
SELECT id, solution_index, start_x, start_y, open1, open2, open3, encode(path,'hex')
FROM path_explorer_solution
WHERE solver_run_id = (SELECT max(id) FROM solver_run WHERE save_mode = 'flat')
ORDER BY id LIMIT 50;

-- en son trie koşusundan 50 düğüm
SELECT id, parent_step_id, step_no, x, y, move_from_parent,
       solution_ordinal, subtree_solution_count, is_leaf
FROM solution_step
WHERE run_id = (SELECT max(id) FROM solver_run WHERE save_mode = 'trie')
ORDER BY id LIMIT 50;
```

---

## 4) "Şu açılıştan kaç çözüm" — trie ile O(derinlik), tarama yok

```sql
-- adım1=(0,0), adım2=(0,3) açılışından kaç çözüm?
WITH r AS (
  SELECT id FROM solution_step
  WHERE run_id = :run AND parent_step_id IS NULL AND x = 0 AND y = 0
)
SELECT s2.subtree_solution_count
FROM solution_step s2, r
WHERE s2.run_id = :run AND s2.parent_step_id = r.id AND s2.x = 0 AND s2.y = 3;
-- -> 248   (yukarıdaki id=2 satırının subtree_cnt'i)
```

Flat karşılığı (tarama gerektirir):
```sql
SELECT count(*) FROM path_explorer_solution
WHERE solver_run_id = :run AND open1 = 0 AND open2 = 3;
```

---

## 5) Bir çözümü geri kur (trie, yaprak → kök)

```sql
-- bir yapraktan köke move'ları topla (recursive CTE)
WITH RECURSIVE up AS (
  SELECT id, parent_step_id, move_from_parent, step_no, x, y
  FROM solution_step WHERE run_id = :run AND id = :leaf_id
  UNION ALL
  SELECT s.id, s.parent_step_id, s.move_from_parent, s.step_no, s.x, s.y
  FROM solution_step s JOIN up ON s.id = up.parent_step_id AND s.run_id = :run
)
SELECT step_no, x, y, move_from_parent FROM up ORDER BY step_no;
```
`(x,y)` sütunları zaten her adımın karesini veriyor → yol doğrudan okunur.
Alternatif: köşe karesinden başlayıp `PathCodec.DIRS[move_from_parent]` ile Java'da oyna.

---

Daha fazlası: `PERSISTENCE.md` (modlar, ayarlar, ölçek), `docker/README.md`
(bağlantı, komutlar), `docker/initdb/*.sql` (şema).
