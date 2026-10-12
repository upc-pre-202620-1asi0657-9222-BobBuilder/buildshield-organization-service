# buildshield-organization-service

Organización y Configuración de BuildShield (BobBuilder): organizaciones, obras, almacenes, catálogo de materiales y
asignación de encargados. Contrato de la arquitectura: `wiki/informe-tf/plan-microservicios.md` del proyecto.

| Dato | Valor |
|---|---|
| Puerto | 8082 |
| Base | `organization_db` (PostgreSQL 16+) |
| Paquete | `pe.buildshield.organization` (kernel compartido copiado de iam en `pe.buildshield.organization.shared`) |
| Publica | `buildshield.organization`: `LocationRegistered`, `LocationUpdated` (WORKSITE o WAREHOUSE), `MaterialRegistered`, `MaterialUpdated`, `StaffAssigned`, `StaffUnassigned` |
| Consume | `buildshield.iam`: `OrganizationRegistered` (fila de `organizations`), `UserCreated` y `UserUpdated` (copia `user_projection`). `buildshield.subscription`: `SubscriptionActivated`, `SubscriptionExpired` y `SubscriptionBillingPeriodChangeScheduled` (copia `subscription_projection`: plan, estado y `maxActiveWorksites`) |

## Endpoints

- `GET /api/v1/organizations/me` y `GET /api/v1/organizations/{id}` (solo la propia)
- `/api/v1/worksites`, `/api/v1/warehouses`, `/api/v1/materials`, `/api/v1/assignments`: POST, GET, GET `/{id}`, PATCH `/{id}`
  con las mismas formas del Core
- El encargado de almacén lee todas las obras de su organización (solo lectura) para ver a qué obra va cada pedido.
- Límite del plan (US07, Sprint 4): con el plan Plus, registrar una obra activa (sin fecha de fin o con fin no vencido)
  o reactivar una terminada cambiando su fecha de fin responde 409 `WORKSITE_LIMIT_REACHED` si ya hay 5 obras
  activas. Pro y la prueba gratuita no tienen límite. El plan sale de la copia local alimentada por eventos (sin
  llamadas a subscription-service) y un bloqueo por organización evita que dos altas simultáneas pasen el límite.
- Cuenta en solo lectura (US10): con la suscripción vencida, todo POST, PUT, PATCH o DELETE responde 402
  `SUBSCRIPTION_READ_ONLY`; las lecturas siguen permitidas (defensa en profundidad del corte del gateway).
- OpenAPI en `/api/v1/api-docs` y `/swagger-ui.html` (perfiles local y test)

## Variables de entorno

`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS` (por defecto `localhost:9092`) y
`BUILDSHIELD_JWT_PUBLIC_KEY`. Perfiles `local`, `test` y `prod`.

## Ejecutar en local (clúster PostgreSQL 127.0.0.1:5433 con autenticación trust)

```bash
mvn -B package -DskipTests
BUILDSHIELD_JWT_PUBLIC_KEY="$(cat ~/.buildshield/jwt-public.pem)" \
java -jar target/buildshield-organization-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

## Pruebas

```bash
mvn -B verify -Dbuildshield.test.external-database=true
```

Las `*IT` usan la base temporal `organization_it` en `127.0.0.1:5433` con el usuario `buildshield_core` y Kafka embebido
(`@EmbeddedKafka`). Sin la propiedad usan Testcontainers (requiere Docker). Cucumber cubre US14 a US17 y US07 (límite
de obras del plan y cuenta en solo lectura). JaCoCo exige 80 % de líneas en `domain` y
`application`.
