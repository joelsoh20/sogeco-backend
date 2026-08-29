-- =====================================================================
-- V25 — La date de depart prevue devient optionnelle
--
-- La date de reference officielle d'une mission est desormais actualStart
-- (fixee par le bouton Demarrer), pas une date planifiee a la saisie.
-- planned_start reste utile pour un dispatcheur qui planifie a l'avance,
-- mais ne bloque plus la creation quand la mission est lancee au fil de
-- l'eau, sans planification prealable.
-- =====================================================================

ALTER TABLE missions ALTER COLUMN planned_start DROP NOT NULL;
