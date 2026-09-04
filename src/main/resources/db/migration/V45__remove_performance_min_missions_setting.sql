-- ---------------------------------------------------------------------
-- Le score de performance chauffeur ne depend plus du nombre de
-- missions cloturees (DriverPerformanceService.recomputeScore()) : un
-- usage quotidien sans mission formelle (tour de ville) laissait sinon
-- ce seuil bloquer indefiniment le calcul du score, meme avec des
-- notes saisies par un responsable. Le parametre n'a plus aucun effet
-- en code -- le retirer evite un reglage d'administration qui semble
-- agir mais ne fait plus rien.
-- ---------------------------------------------------------------------

DELETE FROM system_settings WHERE setting_key = 'performance.min_missions';
