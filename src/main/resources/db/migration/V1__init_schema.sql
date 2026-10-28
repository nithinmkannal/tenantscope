CREATE TABLE tenant (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(160) NOT NULL,
    tier        VARCHAR(20)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tenant_config (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    service     VARCHAR(40)  NOT NULL,
    config_key  VARCHAR(120) NOT NULL,
    config_value VARCHAR(1000),
    sensitive   BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_config UNIQUE (tenant_id, service, config_key)
);

CREATE TABLE tenant_document (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT      NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    doc_type      VARCHAR(40) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_doc_tenant_status ON tenant_document (tenant_id, status);
CREATE INDEX idx_doc_tenant_received ON tenant_document (tenant_id, received_at DESC);

CREATE SEQUENCE incident_number_seq START WITH 1000;

CREATE TABLE incident (
    id               BIGSERIAL PRIMARY KEY,
    number           VARCHAR(20)  NOT NULL UNIQUE,
    tenant_id        BIGINT       NOT NULL REFERENCES tenant (id),
    service          VARCHAR(40)  NOT NULL,
    category         VARCHAR(40)  NOT NULL,
    priority         VARCHAR(4)   NOT NULL,
    state            VARCHAR(20)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      VARCHAR(4000),
    assignment_group VARCHAR(60)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acknowledged_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    resolve_due_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_incident_state ON incident (state);
CREATE INDEX idx_incident_tenant ON incident (tenant_id);
CREATE INDEX idx_incident_created ON incident (created_at);

CREATE TABLE incident_note (
    id          BIGSERIAL PRIMARY KEY,
    incident_id BIGINT      NOT NULL REFERENCES incident (id) ON DELETE CASCADE,
    author      VARCHAR(80) NOT NULL,
    kind        VARCHAR(20) NOT NULL,
    body        VARCHAR(4000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_note_incident ON incident_note (incident_id, created_at);
