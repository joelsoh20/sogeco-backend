-- Le compte Comptable ne donne acces qu'a deux ecrans : Assurance & Visite technique et
-- Rapports & Statistiques. On lui retire donc les droits d'ecriture et de lecture sans rapport
-- avec ces deux ecrans (missions, carburant, maintenance, tarifs, primes). Restent :
--  - assurance et visite technique : INSURANCE_READ / CREATE / UPDATE, PARTNER_READ (assureurs) ;
--  - rapports : REPORT_READ / REPORT_EXPORT, FINANCE_READ, SALARY_READ, CLIENT_READ ;
--  - VEHICLE_READ et DRIVER_READ : listes de camions et classement chauffeurs utilises par ces ecrans.
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE code = 'ROLE_COMPTABLE')
  AND permission_id IN (
      SELECT id FROM permissions WHERE code IN (
          'MISSION_READ', 'FUEL_READ', 'FUEL_UPDATE', 'MAINTENANCE_READ',
          'CLIENT_MANAGE', 'TARIFF_MANAGE', 'DRIVER_BONUS_MANAGE'));

UPDATE roles
SET description = 'Assurance, visite technique, rapports et statistiques'
WHERE code = 'ROLE_COMPTABLE';
