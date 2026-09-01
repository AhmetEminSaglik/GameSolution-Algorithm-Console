-- Cozum algoritmalari referans tablosu. solving_checkpoint (ve ileride diger
-- tablolar) buraya algorithm_id ile baglanir.
--
-- id = uygulamadaki BaseSolution.getSolutionCreatedOrder() (1, 2, 3...).
-- description: algoritmanin NASIL bir islemle cozdugu.

CREATE TABLE IF NOT EXISTS solving_algorithm (
    id          SMALLINT     PRIMARY KEY,
    code        VARCHAR(80)  NOT NULL UNIQUE,   -- kod adi (Java sinif adi)
    name        VARCHAR(120) NOT NULL,          -- kisa ad
    description TEXT         NOT NULL           -- nasil calisir
);

INSERT INTO solving_algorithm (id, code, name, description) VALUES
 (1, 'FirstSolution_Combination', 'Kombinasyon / duz DFS',
  'Sezgisel yok. Her karede pusula yonleri sabit sirayla denenir '
  '(Kuzey, KD, Dogu, GD, Guney, GB, Bati, KB). Hedef kare bos (ziyaret edilmemis) '
  've o yon bu adimda daha once denenmemis ise oraya ilerlenir; hicbir yon uymazsa '
  'geri adim atilir. Tum cozum uzayini sirali gezer - tam ama yavas.'),
 (2, 'SecondSolution_CalculateForwardAvailableWays', 'Ileri bakisli - acik yol sayimi',
  'Warnsdorff benzeri. Her aday yon icin bir sonraki kareden ulasilabilecek acik '
  'kare sayisi hesaplanir; yonler bu sayiya gore agirliklandirilir '
  '(WeightOfAvailableWay) - daha cok secenek birakan / cikmaza sokmayan hamle '
  'tercih edilir. Acik yolu 0 olan yon (cikmaz) elenir. Tek cikisli (one-way) '
  'kareler zorunlu koridor sayilir, RoadMemory''de saklanip tekrar oynatilir; '
  'ust uste 3 tek-yollu kare gorulurse dal cikmaz kabul edilip iptal edilir. '
  'exitSituation ile bulunan zorunlu cikis isaretlenir.'),
 (3, 'ThirdSolution_GoldenSquare', 'Altin kare (henuz yazilmadi)',
  'Placeholder. Graf tabanli, ek ozelliklerle gelecek.')
ON CONFLICT (id) DO NOTHING;
