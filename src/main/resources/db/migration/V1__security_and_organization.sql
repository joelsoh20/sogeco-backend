-- =====================================================================
-- V1 — Securite et organisation
--
-- Tables : cities, agencies, roles, permissions, role_permissions,
--          users, user_roles, refresh_tokens, audit_logs, system_settings
--
-- Conventions (CDC technique, section 5) :
--   - snake_case, cle primaire BIGSERIAL
--   - enums stockes en VARCHAR avec contrainte CHECK
--   - dates en TIMESTAMPTZ (stockage UTC)
--   - colonnes d'audit sur toutes les tables metier
--   - aucune suppression physique : active + deleted_at
-- =====================================================================


-- ---------------------------------------------------------------------
-- VILLES
-- Referentiel ouvert : toute ville de livraison rencontree y est creee.
-- has_site = true uniquement pour Douala, Yaounde, Bafoussam.
-- ---------------------------------------------------------------------
CREATE TABLE cities (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(10)   NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    region          VARCHAR(100),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    has_site        BOOLEAN       NOT NULL DEFAULT FALSE,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,

    version         INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),

    CONSTRAINT uk_cities_code UNIQUE (code),
    CONSTRAINT ck_cities_latitude  CHECK (latitude  IS NULL OR (latitude  BETWEEN -90  AND 90)),
    CONSTRAINT ck_cities_longitude CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180))
);

CREATE INDEX ix_cities_active ON cities (active);


-- ---------------------------------------------------------------------
-- SITES : siege, agences, depots
-- ---------------------------------------------------------------------
CREATE TABLE agencies (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(20)   NOT NULL,
    name            VARCHAR(120)  NOT NULL,
    city_id         BIGINT        NOT NULL,
    site_type       VARCHAR(20)   NOT NULL,
    address         VARCHAR(255),
    phone           VARCHAR(30),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,

    version         INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),

    CONSTRAINT uk_agencies_code UNIQUE (code),
    CONSTRAINT fk_agencies_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT ck_agencies_site_type CHECK (site_type IN ('SIEGE', 'AGENCE', 'DEPOT'))
);

CREATE INDEX ix_agencies_city   ON agencies (city_id);
CREATE INDEX ix_agencies_active ON agencies (active);


-- ---------------------------------------------------------------------
-- ROLES
-- is_system = true : role livre avec l'application, non supprimable.
-- L'administrateur peut creer des roles supplementaires.
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(40)   NOT NULL,
    label           VARCHAR(80)   NOT NULL,
    description     VARCHAR(255),
    is_system       BOOLEAN       NOT NULL DEFAULT FALSE,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,

    version         INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),

    CONSTRAINT uk_roles_code UNIQUE (code)
);


-- ---------------------------------------------------------------------
-- PERMISSIONS
-- Referentiel fige, alimente par migration. Une permission = une action
-- verifiable par @PreAuthorize("hasAuthority('VEHICLE_CREATE')").
-- ---------------------------------------------------------------------
CREATE TABLE permissions (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(60)   NOT NULL,
    module          VARCHAR(40)   NOT NULL,
    label           VARCHAR(150)  NOT NULL,

    version         INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),

    CONSTRAINT uk_permissions_code UNIQUE (code)
);

CREATE INDEX ix_permissions_module ON permissions (module);


CREATE TABLE role_permissions (
    role_id         BIGINT NOT NULL,
    permission_id   BIGINT NOT NULL,

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------
-- UTILISATEURS
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                        BIGSERIAL PRIMARY KEY,
    email                     VARCHAR(150)  NOT NULL,
    password_hash             VARCHAR(255),
    first_name                VARCHAR(80)   NOT NULL,
    last_name                 VARCHAR(80)   NOT NULL,
    phone                     VARCHAR(30),
    agency_id                 BIGINT,
    status                    VARCHAR(20)   NOT NULL DEFAULT 'ACTIF',

    -- Connexion Google (D10) : renseigne au premier rattachement.
    google_id                 VARCHAR(120),

    -- Double authentification TOTP
    totp_secret               VARCHAR(128),
    totp_enabled              BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Verrouillage apres echecs successifs (RG-1.3)
    failed_attempts           INTEGER       NOT NULL DEFAULT 0,
    locked_until              TIMESTAMPTZ,
    last_login_at             TIMESTAMPTZ,

    -- Reinitialisation de mot de passe
    password_reset_token      VARCHAR(120),
    password_reset_expires_at TIMESTAMPTZ,
    must_change_password      BOOLEAN       NOT NULL DEFAULT FALSE,

    deleted_at                TIMESTAMPTZ,

    version                   INTEGER       NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ,
    created_by                VARCHAR(150),
    updated_by                VARCHAR(150),

    CONSTRAINT uk_users_email     UNIQUE (email),
    CONSTRAINT uk_users_google_id UNIQUE (google_id),
    CONSTRAINT fk_users_agency FOREIGN KEY (agency_id) REFERENCES agencies (id),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIF', 'SUSPENDU', 'SUPPRIME')),
    -- Un compte doit disposer d'au moins un moyen d'authentification.
    CONSTRAINT ck_users_credentials CHECK (password_hash IS NOT NULL OR google_id IS NOT NULL)
);

CREATE INDEX ix_users_agency ON users (agency_id);
CREATE INDEX ix_users_status ON users (status);


CREATE TABLE user_roles (
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);


-- ---------------------------------------------------------------------
-- JETONS DE RAFRAICHISSEMENT
-- Stockes hachees : une fuite de la base ne permet pas de les rejouer.
-- Rotation a chaque usage (CDC technique, section 6.1).
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    token_hash      VARCHAR(255)  NOT NULL,
    expires_at      TIMESTAMPTZ   NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_ip      VARCHAR(45),

    version         INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),

    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);


-- ---------------------------------------------------------------------
-- JOURNAL D'AUDIT
-- Table en ajout seul : ni version, ni updated_at, ni suppression.
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_email      VARCHAR(150),
    action          VARCHAR(60)   NOT NULL,
    entity_type     VARCHAR(60),
    entity_id       BIGINT,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_logs_user    ON audit_logs (user_email);
CREATE INDEX ix_audit_logs_entity  ON audit_logs (entity_type, entity_id);


-- ---------------------------------------------------------------------
-- PARAMETRES SYSTEME
-- Aucun seuil metier n'est code en dur (RG-13.1).
-- ---------------------------------------------------------------------
CREATE TABLE system_settings (
    id              BIGSERIAL PRIMARY KEY,
    setting_key     VARCHAR(100)  NOT NULL,
    setting_value   TEXT,
    value_type      VARCHAR(20)   NOT NULL DEFAULT 'STRING',
    category        VARCHAR(40)   NOT NULL,
    label           VARCHAR(150)  NOT NULL,

    version         INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),

    CONSTRAINT uk_system_settings_key UNIQUE (setting_key),
    CONSTRAINT ck_system_settings_type CHECK (value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON'))
);

CREATE INDEX ix_system_settings_category ON system_settings (category);
