-- =====================================================================
-- V8 — Rapports et exports
--
-- Une seule table nouvelle : generated_reports. Le contenu des
-- rapports eux-memes est une agregation en lecture seule sur les
-- donnees deja collectees (missions, carburant, maintenance,
-- depenses) — aucune nouvelle table metier n'est necessaire pour ca.
--
-- generated_reports garde la trace de chaque export : qui, quand,
-- quel format, et le chemin du fichier produit. C'est aussi ce que la
-- generation periodique alimente, pour que l'utilisateur retrouve un
-- rapport deja pret sans le regenerer.
-- =====================================================================

CREATE TABLE generated_reports (
    id                  BIGSERIAL PRIMARY KEY,
    report_type         VARCHAR(40)   NOT NULL,
    format              VARCHAR(10)   NOT NULL,
    period_start        DATE          NOT NULL,
    period_end          DATE          NOT NULL,
    file_path           VARCHAR(500)  NOT NULL,
    row_count           INTEGER       NOT NULL DEFAULT 0,
    requested_by_user_id BIGINT,
    is_scheduled        BOOLEAN       NOT NULL DEFAULT FALSE,
    generated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_reports_user FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_reports_type CHECK (report_type IN
        ('RENTABILITE_CAMIONS', 'RENTABILITE_MISSIONS', 'RENTABILITE_CLIENTS',
         'RENTABILITE_CORRIDORS', 'RENTABILITE_AGENCES', 'SYNTHESE_MENSUELLE')),
    CONSTRAINT ck_reports_format CHECK (format IN ('CSV', 'XLSX')),
    CONSTRAINT ck_reports_period CHECK (period_end >= period_start)
);

CREATE INDEX ix_reports_type ON generated_reports (report_type, generated_at DESC);
CREATE INDEX ix_reports_user ON generated_reports (requested_by_user_id, generated_at DESC);


-- ---------------------------------------------------------------------
-- PARAMETRES COMPLEMENTAIRES
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('reports.retention_days',        '365',  'INTEGER', 'RAPPORTS', 'Duree de conservation des rapports generes (jours)', 'system'),
    ('reports.monthly_auto_generate', 'true', 'BOOLEAN', 'RAPPORTS', 'Generer automatiquement la synthese mensuelle',       'system');
