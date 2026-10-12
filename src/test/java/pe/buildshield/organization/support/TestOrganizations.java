package pe.buildshield.organization.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.buildshield.organization.shared.tenant.TenantInfo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Datos que en producción llegan desde iam por eventos: la organización ({@code OrganizationRegistered}) y la
 * copia de sus usuarios ({@code UserCreated}). Las pruebas los escriben directo en la base o arman el sobre del
 * evento para enviarlo a Kafka.
 */
public final class TestOrganizations {

    private TestOrganizations() {
    }

    /** Organización con su administrador; devuelve el contexto del administrador. */
    public static TenantInfo register(JdbcTemplate jdbc, String ruc, String legalName) {
        UUID organizationId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, ruc, legal_name, version, created_at) VALUES (?, ?, ?, 0, ?)",
                organizationId, ruc, legalName, Timestamp.from(Instant.now()));
        UUID admin = addUser(jdbc, organizationId, "admin@" + ruc + ".pe", "ADMINISTRATOR");
        return new TenantInfo(organizationId, admin, "ADMINISTRATOR");
    }

    /** Usuario activo de la organización en la copia local; devuelve su id. */
    public static UUID addUser(JdbcTemplate jdbc, UUID organizationId, String email, String role) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_projection (user_id, organization_id, full_name, email, role, active, updated_at)
                VALUES (?, ?, ?, ?, ?, true, ?)""", userId, organizationId, email, email, role,
                Timestamp.from(Instant.now()));
        return userId;
    }

    /** Sobre JSON de un evento de iam, como lo publica su outbox. */
    public static String iamEvent(ObjectMapper json, String eventType, UUID organizationId, String aggregateType,
            UUID aggregateId, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("organizationId", organizationId);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("actorId", null);
        envelope.put("version", 1);
        envelope.put("payload", payload);
        try {
            return json.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Evento de subscription ({@code SubscriptionActivated}, {@code SubscriptionExpired}, ...) con el estado de la
     * suscripción, como lo publica su outbox.
     */
    public static String subscriptionEvent(ObjectMapper json, String eventType, UUID organizationId, String plan,
            String status, Integer maxActiveWorksites, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organizationId", organizationId);
        payload.put("subscriptionId", UUID.randomUUID());
        payload.put("plan", plan);
        payload.put("status", status);
        payload.put("validUntil", occurredAt.plusSeconds(30L * 86_400).toString());
        payload.put("billingPeriod", "FREE".equals(plan) ? null : "MONTHLY");
        payload.put("maxActiveWorksites", maxActiveWorksites);
        payload.put("reason", "PAYMENT_CONFIRMED");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("organizationId", organizationId);
        envelope.put("aggregateType", "SUBSCRIPTION");
        envelope.put("aggregateId", payload.get("subscriptionId").toString());
        envelope.put("actorId", null);
        envelope.put("version", 1);
        envelope.put("payload", payload);
        try {
            return json.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String organizationRegistered(ObjectMapper json, UUID organizationId, String ruc, String legalName,
            UUID adminUserId, String adminEmail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organizationId", organizationId);
        payload.put("ruc", ruc);
        payload.put("legalName", legalName);
        payload.put("adminUserId", adminUserId);
        payload.put("adminEmail", adminEmail);
        return iamEvent(json, "OrganizationRegistered", organizationId, "ORGANIZATION", organizationId, payload);
    }

    public static String userEvent(ObjectMapper json, String eventType, UUID organizationId, UUID userId,
            String fullName, String email, String role, boolean active) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("fullName", fullName);
        payload.put("email", email);
        payload.put("role", role);
        payload.put("active", active);
        return iamEvent(json, eventType, organizationId, "USER", userId, payload);
    }
}
