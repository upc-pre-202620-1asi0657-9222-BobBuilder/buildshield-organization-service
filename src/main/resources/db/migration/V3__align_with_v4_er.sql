-- Alineación con el ER v4 (pág. 15, organization_db y Anexo A.5).
-- Una sola tabla locations para obras y almacenes, location_assignments para el personal asignado, nombres de
-- columna del ER, unit_weight_kg en materials y las tablas técnicas del outbox y del consumidor idempotente.
-- Los identificadores se conservan: ningún servicio que referencie un id de obra o almacén se ve afectado.

-- organizations: business_name varchar(150) y ruc char(11).
ALTER TABLE organizations RENAME COLUMN legal_name TO business_name;
ALTER TABLE organizations ALTER COLUMN business_name TYPE VARCHAR(150);
ALTER TABLE organizations ALTER COLUMN ruc TYPE CHAR(11);

-- locations: obras (WORKSITE) y almacenes (WAREHOUSE) en una sola tabla. Las columnas warehouse_type, district,
-- city, latitude y longitude no están en el ER: las usan los almacenes (centro de acopio) y las obras (ubicación).
CREATE TABLE locations (
    id               UUID PRIMARY KEY,
    organization_id  UUID             NOT NULL REFERENCES organizations (id),
    type             VARCHAR(10)      NOT NULL,
    name             VARCHAR(120)     NOT NULL,
    address          VARCHAR(200)     NOT NULL,
    start_date       DATE,
    planned_end_date DATE,
    active           BOOLEAN          NOT NULL,
    warehouse_type   VARCHAR(20),
    district         VARCHAR(100),
    city             VARCHAR(100),
    latitude         DOUBLE PRECISION,
    longitude        DOUBLE PRECISION,
    version          BIGINT           NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL,
    created_by       UUID             NOT NULL,
    updated_at       TIMESTAMPTZ      NOT NULL,
    updated_by       UUID             NOT NULL,
    CONSTRAINT ck_locations_type CHECK (type IN ('WORKSITE', 'WAREHOUSE')),
    CONSTRAINT ck_locations_worksite CHECK (type <> 'WORKSITE' OR (start_date IS NOT NULL AND district IS NOT NULL AND city IS NOT NULL)),
    CONSTRAINT ck_locations_warehouse CHECK (type <> 'WAREHOUSE' OR warehouse_type IN ('WAREHOUSE', 'COLLECTION_CENTER')),
    CONSTRAINT ck_locations_dates CHECK (planned_end_date IS NULL OR start_date IS NULL OR planned_end_date >= start_date)
);

CREATE INDEX idx_locations_organization ON locations (organization_id, type, name);

-- Para una obra, active es la vigencia al último cambio; el límite del plan y las consultas usan las fechas.
INSERT INTO locations (id, organization_id, type, name, address, start_date, planned_end_date, active, district, city,
                       latitude, longitude, version, created_at, created_by, updated_at, updated_by)
SELECT id, organization_id, 'WORKSITE', name, address, start_date, end_date,
       (end_date IS NULL OR end_date >= CURRENT_DATE), district, city, latitude, longitude, version,
       created_at, created_by, updated_at, updated_by
FROM worksites;

INSERT INTO locations (id, organization_id, type, name, address, active, warehouse_type, version, created_at,
                       created_by, updated_at, updated_by)
SELECT id, organization_id, 'WAREHOUSE', name, address, active, type, version, created_at, created_by, updated_at,
       updated_by
FROM warehouses;

-- location_assignments: personal asignado a una obra o un almacén. role es el rol del usuario en iam; ended_at
-- conserva la asignación terminada como historial (no está en el ER).
CREATE TABLE location_assignments (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    location_id     UUID        NOT NULL REFERENCES locations (id),
    user_id         UUID        NOT NULL,
    role            VARCHAR(20) NOT NULL,
    assigned_at     TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ,
    version         BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID        NOT NULL,
    CONSTRAINT ck_location_assignments_role CHECK (role IN ('SITE_MANAGER', 'WAREHOUSE_MANAGER')),
    CONSTRAINT ck_location_assignments_dates CHECK (ended_at IS NULL OR ended_at >= assigned_at)
);

INSERT INTO location_assignments (id, organization_id, location_id, user_id, role, assigned_at, ended_at, version,
                                  created_at, created_by, updated_at, updated_by)
SELECT id, organization_id, COALESCE(worksite_id, warehouse_id), user_id,
       CASE site_type WHEN 'WORKSITE' THEN 'SITE_MANAGER' ELSE 'WAREHOUSE_MANAGER' END,
       assigned_at, ended_at, version, created_at, created_by, updated_at, updated_by
FROM staff_assignments;

-- Una sola asignación activa por usuario y lugar.
CREATE UNIQUE INDEX uk_location_assignments_active ON location_assignments (user_id, location_id) WHERE ended_at IS NULL;
CREATE INDEX idx_location_assignments_organization ON location_assignments (organization_id, location_id);
CREATE INDEX idx_location_assignments_user ON location_assignments (user_id) WHERE ended_at IS NULL;

DROP TABLE staff_assignments;
DROP TABLE warehouses;
DROP TABLE worksites;

-- materials: tolerance_pct, unit_weight_kg (lo usa el pesaje de ordering) y llave foránea a organizations.
-- El SKU es único por organización (uk_materials_organization_sku): con una restricción global dos organizaciones
-- no podrían tener el mismo SKU.
ALTER TABLE materials RENAME COLUMN waste_tolerance_percent TO tolerance_pct;
ALTER TABLE materials RENAME CONSTRAINT ck_materials_waste_tolerance TO ck_materials_tolerance;
ALTER TABLE materials ALTER COLUMN name TYPE VARCHAR(120);
ALTER TABLE materials ADD COLUMN unit_weight_kg NUMERIC(10, 3) NOT NULL DEFAULT 0;
ALTER TABLE materials ALTER COLUMN unit_weight_kg DROP DEFAULT;
ALTER TABLE materials ADD CONSTRAINT ck_materials_unit_weight CHECK (unit_weight_kg >= 0);
ALTER TABLE materials ADD CONSTRAINT fk_materials_organization FOREIGN KEY (organization_id) REFERENCES organizations (id);

-- outbox_events (A.5): sin aggregate_type (se deduce del event_type) y con aggregate_id uuid.
-- actor_id y correlation_id no están en el ER: van en el sobre del evento.
ALTER TABLE outbox_events DROP COLUMN aggregate_type;
ALTER TABLE outbox_events ALTER COLUMN aggregate_id TYPE UUID USING aggregate_id::uuid;
ALTER TABLE outbox_events ALTER COLUMN event_type TYPE VARCHAR(60);

-- processed_events (A.5): llave (event_id, consumer). Lo procesado hasta ahora lo hicieron los consumidores de
-- iam y de subscription, que ahora son OrganizationEventConsumer.
ALTER TABLE processed_events ADD COLUMN consumer VARCHAR(60) NOT NULL DEFAULT 'OrganizationEventConsumer';
ALTER TABLE processed_events ALTER COLUMN consumer DROP DEFAULT;
ALTER TABLE processed_events DROP CONSTRAINT processed_events_pkey;
ALTER TABLE processed_events ADD PRIMARY KEY (event_id, consumer);
