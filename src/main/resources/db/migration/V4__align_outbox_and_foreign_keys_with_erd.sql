-- Alineación con el ER corregido de organization_db (wiki/informe-avance2/er-corregido.md).

-- outbox_events: sin actor_id (no está en el ER). El sobre completo del evento (con aggregateType y actorId) va en
-- payload, como en iam; el publicador lo envía tal cual. Los eventos ya escritos se convierten al mismo formato.
UPDATE outbox_events SET payload = jsonb_build_object(
        'eventId', id, 'eventType', event_type,
        'occurredAt', to_char(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        'organizationId', organization_id,
        'aggregateType', CASE WHEN event_type LIKE 'Staff%' THEN 'Assignment'
                              WHEN event_type LIKE 'Location%' THEN 'Location'
                              WHEN event_type LIKE 'Material%' THEN 'Material'
                              ELSE regexp_replace(event_type, '[A-Z][a-z0-9]*$', '') END,
        'aggregateId', aggregate_id, 'actorId', actor_id, 'version', 1, 'payload', payload)
WHERE NOT (payload ? 'eventId');
ALTER TABLE outbox_events DROP COLUMN actor_id;

-- Llaves foráneas hacia organizations que el ER pide y faltaban.
-- user_projection y subscription_projection quedan sin llave: sus filas las crean eventos de otros tópicos de Kafka
-- (UserCreated, Subscription*) que pueden llegar antes que OrganizationRegistered, y el consumidor perdería el
-- evento tras agotar los reintentos. Ver DESVIACIONES.md.
ALTER TABLE location_assignments ADD CONSTRAINT fk_location_assignments_organization
    FOREIGN KEY (organization_id) REFERENCES organizations (id);
ALTER TABLE outbox_events ADD CONSTRAINT fk_outbox_events_organization
    FOREIGN KEY (organization_id) REFERENCES organizations (id);
