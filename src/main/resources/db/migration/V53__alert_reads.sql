-- Alertes deja consultees, par utilisateur : le compteur de la cloche ne compte que les alertes
-- ouvertes non consultees. Une alerte qui se repete (occurrences en hausse) redevient non lue.
CREATE TABLE alert_reads (
    alert_id    BIGINT      NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id)  ON DELETE CASCADE,
    occurrences INTEGER     NOT NULL,
    read_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (alert_id, user_id)
);

CREATE INDEX idx_alert_reads_user ON alert_reads (user_id);
