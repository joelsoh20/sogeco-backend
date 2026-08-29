-- =====================================================================
-- V26 — Missions automatisees (livraisons quotidiennes recurrentes)
--
-- Un modele reutilisable (ville, trajet, camion, chauffeur fixes) a
-- partir duquel une mission est generee chaque jour a 9h30
-- (MissionAutomationScheduler). Le gestionnaire annule simplement la
-- mission du jour (mecanisme d'annulation deja existant) quand la
-- livraison n'est pas necessaire — l'automatisation elle-meme continue
-- de tourner tant qu'elle n'est pas desactivee (active = false).
-- =====================================================================

CREATE TABLE mission_automations (
    id                       BIGSERIAL PRIMARY KEY,
    version                  INTEGER NOT NULL DEFAULT 0,
    label                    VARCHAR(150),
    city_id                  BIGINT NOT NULL REFERENCES cities(id),
    service_type_id          BIGINT NOT NULL REFERENCES service_types(id),
    client_id                BIGINT REFERENCES clients(id),
    vehicle_id               BIGINT NOT NULL REFERENCES vehicles(id),
    driver_id                BIGINT NOT NULL REFERENCES drivers(id),
    agency_id                BIGINT NOT NULL REFERENCES agencies(id),
    destination_quartier_id  BIGINT NOT NULL REFERENCES quartiers(id),
    cargo_description        VARCHAR(255),
    active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ,
    created_by               VARCHAR(150),
    updated_by               VARCHAR(150)
);

CREATE INDEX idx_mission_automations_active ON mission_automations(active);

-- Trace quelle automatisation a genere une mission donnee — sert a
-- l'anti-doublon quotidien du scheduler (une seule mission par
-- automatisation et par jour).
ALTER TABLE missions ADD COLUMN mission_automation_id BIGINT REFERENCES mission_automations(id);
