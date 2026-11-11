-- Le gestionnaire peut tout faire dans l'application SAUF les parametres. On lui ajoute donc les droits
-- qui lui manquaient hors parametres : clients et tarifs, finances et salaires, primes des chauffeurs,
-- tableau de bord de direction, zones geographiques (geofences) et creation de ville (utilisee par les
-- formulaires de mission pour ajouter un lieu absent du referentiel).
--
-- Restent reserves a l'administrateur (ecran Parametres) : SETTING_MANAGE, USER_MANAGE, ROLE_MANAGE,
-- INTEGRATION_MANAGE, AGENCY_MANAGE (sites), ALERT_RULE_MANAGE (seuils d'alerte) et AUDIT_READ.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'ROLE_GESTIONNAIRE'
  AND p.code IN ('CLIENT_MANAGE', 'TARIFF_MANAGE', 'FINANCE_READ', 'SALARY_READ', 'DRIVER_BONUS_MANAGE',
                 'DASHBOARD_EXECUTIVE_READ', 'GEOFENCE_MANAGE', 'CITY_MANAGE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

UPDATE roles
SET description = 'Toute l''application, hors parametres'
WHERE code = 'ROLE_GESTIONNAIRE';
