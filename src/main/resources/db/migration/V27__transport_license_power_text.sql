-- =====================================================================
-- V27 — Puissance de la licence de transport en texte libre
--
-- Jusqu'ici numerique (CV fiscal), mais certaines licences reelles
-- portent une puissance mixte lettres/chiffres (classe de vehicule,
-- notation locale...) — le champ doit accepter n'importe quel libelle.
-- =====================================================================

ALTER TABLE transport_licenses ALTER COLUMN power TYPE VARCHAR(30) USING power::VARCHAR(30);
