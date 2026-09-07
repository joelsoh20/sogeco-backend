-- ---------------------------------------------------------------------
-- Plancher de mouvement GPS (TelematicsIngestionService.computeDistance) :
-- en-dessous de cette distance entre deux trames consecutives, le
-- deplacement est considere comme du bruit de positionnement (camion a
-- l'arret) plutot qu'un vrai trajet. Signale par un utilisateur dont le
-- camion, immobile depuis le matin, affichait tout de meme un km du
-- jour non nul -- la derive GPS s'accumulait trame apres trame.
-- ---------------------------------------------------------------------

INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by)
SELECT 'gps.min_movement_meters', '20', 'INTEGER', 'TELEMATIQUE',
       'Distance minimale (m) entre deux trames pour compter un deplacement reel', 'system'
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'gps.min_movement_meters');
