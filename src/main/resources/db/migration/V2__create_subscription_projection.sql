-- Sprint 4 (US07 y US10): copia local del plan de cada organización, alimentada por los eventos de
-- buildshield.subscription (SubscriptionActivated, SubscriptionExpired y cambios de modalidad). Con ella
-- organization aplica el límite de obras activas del plan (Plus: 5) al crear o reactivar una obra, y rechaza
-- los cambios de una cuenta en solo lectura, sin llamar a subscription-service.
CREATE TABLE subscription_projection (
    organization_id      UUID PRIMARY KEY,
    plan                 VARCHAR(10) NOT NULL,
    status               VARCHAR(10) NOT NULL,
    -- Obras activas que admite el plan; nulo = ilimitadas (prueba gratuita y Pro).
    max_active_worksites INT,
    valid_until          TIMESTAMPTZ,
    -- occurredAt del último evento aplicado: un evento más antiguo que llegue tarde no pisa a uno más nuevo.
    event_occurred_at    TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_subscription_projection_limit CHECK (max_active_worksites IS NULL OR max_active_worksites > 0)
);
