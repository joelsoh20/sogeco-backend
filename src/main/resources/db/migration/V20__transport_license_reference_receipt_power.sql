-- =====================================================================
-- V20 — Licence de transport : reference (renomme depuis license_number),
--        numero de recu et puissance, a la place du numero de licence.
-- =====================================================================

ALTER TABLE transport_licenses RENAME COLUMN license_number TO reference;

ALTER TABLE transport_licenses ADD COLUMN receipt_number VARCHAR(50);
ALTER TABLE transport_licenses ADD COLUMN power NUMERIC(6,2);
