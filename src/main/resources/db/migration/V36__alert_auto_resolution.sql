-- ---------------------------------------------------------------------
-- Permet une resolution d'alerte sans auteur humain : une perte de
-- signal se resout desormais toute seule des qu'une position revient
-- (TelematicsIngestionService.process -> AlertService.resolveSignalRestored),
-- sans qu'un utilisateur n'ait rien fait. La contrainte d'origine
-- (V6) exigeait un resolved_by_user_id non nul pour tout statut
-- RESOLUE -- correct pour une resolution humaine, bloquant pour une
-- resolution automatique constatee sur donnees reelles. resolved_at
-- reste obligatoire dans les deux cas.
-- ---------------------------------------------------------------------

ALTER TABLE alerts DROP CONSTRAINT ck_alerts_resolution;

ALTER TABLE alerts ADD CONSTRAINT ck_alerts_resolution CHECK (
    status <> 'RESOLUE' OR resolved_at IS NOT NULL);
