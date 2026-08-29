-- =====================================================================
-- V22 — Carte bleue : numero de recu et categorie a la place du numero
--        de carte et de l'autorite emettrice, plus la puissance (CV).
--
-- Pas de CHECK sur category : la seule valeur connue a ce jour est
-- "S6-Marchandise compte propre" (categorie de transport pour compte
-- propre) — une liste complete pourrait suivre plus tard sans nouvelle
-- migration puisque la colonne reste un simple texte libre.
-- =====================================================================

ALTER TABLE cartes_bleues RENAME COLUMN card_number TO receipt_number;
ALTER TABLE cartes_bleues RENAME COLUMN issuing_authority TO category;

ALTER TABLE cartes_bleues ADD COLUMN power NUMERIC(6,2);
