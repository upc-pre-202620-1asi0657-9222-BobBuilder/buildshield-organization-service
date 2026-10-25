-- Organización y Configuración (organization_db). Portado de V3, V5, V6, V7 y V8 del Core (esquema
-- organization), ahora en el esquema public de su propia base.

-- La organización es el tenant: su id es el organization_id del resto de tablas. La fila llega con
-- OrganizationRegistered (buildshield.iam); el RUC único lo garantiza iam (registered_rucs).
CREATE TABLE organizations (
    id         UUID PRIMARY KEY,
    ruc        VARCHAR(11)  NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    version    BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_organizations_ruc UNIQUE (ruc),
    CONSTRAINT ck_organizations_ruc_digits CHECK (ruc ~ '^[0-9]{11}$')
);

-- US14: obras de cada organización.
CREATE TABLE worksites (
    id              UUID PRIMARY KEY,
    organization_id UUID             NOT NULL,
    name            VARCHAR(150)     NOT NULL,
    address         VARCHAR(200)     NOT NULL,
    district        VARCHAR(100)     NOT NULL,
    city            VARCHAR(100)     NOT NULL,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    start_date      DATE             NOT NULL,
    end_date        DATE,
    version         BIGINT           NOT NULL,
    created_at      TIMESTAMPTZ      NOT NULL,
    created_by      UUID             NOT NULL,
    updated_at      TIMESTAMPTZ      NOT NULL,
    updated_by      UUID             NOT NULL,
    CONSTRAINT ck_worksites_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_worksites_organization ON worksites (organization_id);

-- US15: almacenes y centros de acopio. No se borran: se desactivan (active = false).
CREATE TABLE warehouses (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    name            VARCHAR(150) NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    address         VARCHAR(200) NOT NULL,
    active          BOOLEAN      NOT NULL,
    version         BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID         NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      UUID         NOT NULL,
    CONSTRAINT ck_warehouses_type CHECK (type IN ('WAREHOUSE', 'COLLECTION_CENTER'))
);

CREATE INDEX idx_warehouses_organization ON warehouses (organization_id);

-- US16: catálogo de materiales. SKU único por organización; tolerancia de merma entre 0 y 100 %.
CREATE TABLE materials (
    id                      UUID PRIMARY KEY,
    organization_id         UUID          NOT NULL,
    sku                     VARCHAR(40)   NOT NULL,
    name                    VARCHAR(150)  NOT NULL,
    unit                    VARCHAR(10)   NOT NULL,
    waste_tolerance_percent NUMERIC(5, 2) NOT NULL,
    active                  BOOLEAN       NOT NULL,
    version                 BIGINT        NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    created_by              UUID          NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    updated_by              UUID          NOT NULL,
    CONSTRAINT uk_materials_organization_sku UNIQUE (organization_id, sku),
    CONSTRAINT ck_materials_waste_tolerance CHECK (waste_tolerance_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_materials_unit CHECK (unit IN ('UNIT', 'KG', 'TONNE', 'M', 'M2', 'M3', 'LITER', 'BAG', 'BOX', 'ROLL'))
);

-- US17: asignación de encargados a obras y almacenes. Al terminar se conserva (ended_at) como historial.
-- user_id es un usuario de iam (copia en user_projection): sin llave foránea.
CREATE TABLE staff_assignments (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    site_type       VARCHAR(20) NOT NULL,
    worksite_id     UUID REFERENCES worksites (id),
    warehouse_id    UUID REFERENCES warehouses (id),
    assigned_at     TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ,
    version         BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID        NOT NULL,
    CONSTRAINT ck_staff_assignments_site CHECK (
        (site_type = 'WORKSITE' AND worksite_id IS NOT NULL AND warehouse_id IS NULL)
        OR (site_type = 'WAREHOUSE' AND warehouse_id IS NOT NULL AND worksite_id IS NULL)),
    CONSTRAINT ck_staff_assignments_dates CHECK (ended_at IS NULL OR ended_at >= assigned_at)
);

-- Una sola asignación activa por usuario y lugar.
CREATE UNIQUE INDEX uk_staff_assignments_active_worksite
    ON staff_assignments (user_id, worksite_id) WHERE ended_at IS NULL AND worksite_id IS NOT NULL;
CREATE UNIQUE INDEX uk_staff_assignments_active_warehouse
    ON staff_assignments (user_id, warehouse_id) WHERE ended_at IS NULL AND warehouse_id IS NOT NULL;

CREATE INDEX idx_staff_assignments_user ON staff_assignments (user_id) WHERE ended_at IS NULL;

-- Copia de los usuarios de iam (UserCreated y UserUpdated de buildshield.iam): rol y estado para
-- validar las asignaciones (US17) y bloquear tokens de usuarios desactivados o con otro rol.
CREATE TABLE user_projection (
    user_id         UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    full_name       VARCHAR(150),
    email           VARCHAR(254),
    role            VARCHAR(30)  NOT NULL,
    active          BOOLEAN      NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_user_projection_organization ON user_projection (organization_id);

-- Transactional Outbox (kernel compartido): eventos escritos en la misma transacción que el cambio de
-- negocio; OutboxPublisher los envía a Kafka y marca published_at.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    organization_id UUID         NOT NULL,
    actor_id        UUID,
    correlation_id  VARCHAR(64),
    payload         JSONB        NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (occurred_at, id) WHERE published_at IS NULL;

-- Consumidor idempotente (kernel compartido): un evento se procesa una sola vez.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
