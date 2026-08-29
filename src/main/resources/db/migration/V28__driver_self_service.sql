-- =====================================================================
-- V28 — Espace chauffeur
--
-- Chaque chauffeur recoit ses missions et peut saisir lui-meme ses
-- visites techniques, sinistres et cartes grises pour le camion qui
-- lui est actuellement affecte. Il ne modifie jamais ce qu'il a saisi
-- et ne voit que ses propres entrees (created_by_user_id).
-- =====================================================================

INSERT INTO permissions (code, module, label, created_by) VALUES
    ('SELF_MANAGE', 'SELF', 'Saisir ses propres visites techniques, sinistres et cartes grises', 'system');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ROLE_CHAUFFEUR' AND p.code = 'SELF_MANAGE';

ALTER TABLE technical_inspections ADD COLUMN created_by_user_id BIGINT;
ALTER TABLE technical_inspections ADD CONSTRAINT fk_inspections_user FOREIGN KEY (created_by_user_id) REFERENCES users (id);

ALTER TABLE cartes_grises ADD COLUMN created_by_user_id BIGINT;
ALTER TABLE cartes_grises ADD CONSTRAINT fk_cartes_grises_user FOREIGN KEY (created_by_user_id) REFERENCES users (id);
