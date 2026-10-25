# Desviaciones frente a la arquitectura v4 · buildshield-organization-service

Fuente fija: `buildshield-presentacion-v4.pdf` (ER pág. 15, C4 pág. 25). Todo lo que no figura ahí y se conserva va aquí con su motivo.

## Tablas extra

| Tabla | Motivo |
|---|---|
| `subscription_projection` | Copia local del plan, alimentada por `SubscriptionActivated` y `SubscriptionExpired`. Sin ella no se puede aplicar el límite de 5 obras activas del plan Plus (409 `WORKSITE_LIMIT_REACHED`) ni rechazar los cambios de una cuenta en solo lectura sin llamar a subscription por REST. |
| `user_projection` | Copia local de los usuarios de iam (`UserCreated` y `UserUpdated`). Se usa para validar el rol y el estado del usuario al asignarlo (US17) y para bloquear tokens de usuarios desactivados o con otro rol. |

## Columnas extra en tablas del ER

| Tabla | Columnas | Motivo |
|---|---|---|
| todas las de negocio (`locations`, `location_assignments`, `materials`) | `version`, `created_at`, `created_by`, `updated_at`, `updated_by` | Bloqueo optimista y auditoría de cambios (`AuditableAbstractAggregateRoot`). `organizations` lleva `version` y `created_at`. |
| `locations` | `warehouse_type` | Distingue almacén de centro de acopio (`WAREHOUSE` o `COLLECTION_CENTER`); el ER solo separa `WORKSITE` de `WAREHOUSE`. |
| `locations` | `district`, `city`, `latitude`, `longitude` | Ubicación de la obra (US14). La usan el registro y la consulta de obras. |
| `location_assignments` | `organization_id` | Toda tabla de negocio lleva la organización para el filtro multiempresa. |
| `location_assignments` | `assigned_at`, `ended_at` | Una asignación terminada se conserva como historial (US17). |
| `outbox_events` | `actor_id`, `correlation_id` | Van en el sobre del evento (`actorId` y la cabecera de correlación). |

## Diferencias de definición

- **`materials.sku`**: el ER lo marca `UNIQUE`. Se mantiene único **por organización** (`uk_materials_organization_sku`, `organization_id + sku`). Con una restricción global, dos organizaciones no podrían usar el mismo SKU y se rompe el aislamiento multiempresa. Pendiente de confirmar con Andreow.
- **`locations.active` en obras**: una obra está vigente si no tiene fecha de fin o su fin no venció. `active` guarda esa vigencia en el último cambio; las consultas y el límite del plan usan las fechas.
- **`materials.unit_weight_kg`**: el ER la pide no nula. La API la acepta opcional al registrar un material y, si falta, queda en 0 para no romper a los clientes anteriores.
- **Nombres**: se acortan a los máximos del ER (`name` 120 y `business_name` 150). La API de organizaciones sigue exponiendo `legalName` y la de materiales `wasteTolerancePercent`, para no cambiar el contrato con el gateway.

## Componentes

- `OrganizationEventConsumer`: el C4 no dibuja consumidor, pero el servicio consume `OrganizationRegistered` y `UserCreated`/`UserUpdated` de iam y `SubscriptionActivated`/`SubscriptionExpired` de subscription. Es el valor de `processed_events.consumer`.
- Los servicios por agregado (`WorksiteService`, `WarehouseService`, `MaterialService`, `AssignmentService`, `OrganizationService`) se conservan como clases internas; los controladores solo usan `OrganizationCommandService` y `OrganizationQueryService`.
- Hay un puerto de persistencia por agregado (`WorksiteRepository`, `WarehouseRepository`, etc.) además de `OrganizationRepository`. Obras y almacenes comparten la tabla `locations` y la entidad JPA `LocationJpaEntity`.
- El agregado `Miembro` del C4 no existe como clase de dominio: los usuarios se leen de `user_projection` a través de `StaffDirectory`.

## Eventos

- `aggregateType` del sobre ahora es `Location`, `Material` y `Assignment` (antes `LOCATION`, `MATERIAL` y `ASSIGNMENT`), como en el ejemplo del Anexo A.6. `outbox_events` ya no lo guarda: se deduce del `event_type`.
- `MaterialRegistered` y `MaterialUpdated` agregan `unitWeightKg`; `StaffAssigned` y `StaffUnassigned` agregan `role`.
