-- =====================================================================
-- V29 — Retrait des roles Direction Generale, Superviseur et Agent de flotte
--
-- Aucun compte n'est affecte a Direction Generale ni a Superviseur.
-- Agent de flotte est retire au profit de Gestionnaire, qui couvre deja
-- le meme perimetre operationnel. Les regles d'alerte qui ne
-- notifiaient QUE l'un de ces roles basculent sur Gestionnaire pour ne
-- pas perdre tout destinataire.
-- =====================================================================

UPDATE alert_rules
SET notify_role_codes = COALESCE(
    NULLIF(
        trim(both ',' from replace(replace(replace(
            ',' || notify_role_codes || ',',
            ',ROLE_DIRECTION,', ','),
            ',ROLE_SUPERVISEUR,', ','),
            ',ROLE_AGENT_FLOTTE,', ',')),
        ''),
    'ROLE_GESTIONNAIRE')
WHERE notify_role_codes ~ 'ROLE_DIRECTION|ROLE_SUPERVISEUR|ROLE_AGENT_FLOTTE';

DELETE FROM user_roles WHERE role_id IN (
    SELECT id FROM roles WHERE code IN ('ROLE_DIRECTION', 'ROLE_SUPERVISEUR', 'ROLE_AGENT_FLOTTE')
);

-- role_permissions se nettoie tout seul (ON DELETE CASCADE sur role_id).
DELETE FROM roles WHERE code IN ('ROLE_DIRECTION', 'ROLE_SUPERVISEUR', 'ROLE_AGENT_FLOTTE');
