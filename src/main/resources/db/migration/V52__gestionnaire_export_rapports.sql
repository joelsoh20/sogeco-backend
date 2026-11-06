-- Le gestionnaire consulte deja les rapports de sa ville (REPORT_READ) mais ne pouvait pas
-- telecharger le rapport PDF des depenses par camion (REPORT_EXPORT) : acces refuse en ligne,
-- alors que le compte administrateur local, qui a toutes les permissions, ne le voyait pas.
-- Les donnees restent limitees a sa ville (RG-13.4).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'ROLE_GESTIONNAIRE'
  AND p.code = 'REPORT_EXPORT'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
