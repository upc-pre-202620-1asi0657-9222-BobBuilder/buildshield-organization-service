package pe.buildshield.organization.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.buildshield.organization.application.OrganizationCommandService;
import pe.buildshield.organization.infrastructure.persistence.JdbcSubscriptionProjection;
import pe.buildshield.organization.infrastructure.persistence.JdbcUserProjection;
import pe.buildshield.organization.shared.messaging.EventEnvelope;
import pe.buildshield.organization.shared.messaging.IdempotentEventProcessor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Consumidor Kafka de organization. Cada evento se procesa una sola vez por este consumidor
 * ({@link IdempotentEventProcessor}, {@code processed_events}); los tipos que no le interesan se ignoran.
 *
 * <ul>
 *   <li>{@code buildshield.iam}: {@code OrganizationRegistered} crea la fila de {@code organizations};
 *       {@code UserCreated} y {@code UserUpdated} actualizan la copia local de usuarios ({@code user_projection}),
 *       que valida el rol al asignar encargados (US17) y bloquea tokens de usuarios desactivados.</li>
 *   <li>{@code buildshield.subscription}: {@code SubscriptionActivated}, {@code SubscriptionExpired} y
 *       {@code SubscriptionBillingPeriodChangeScheduled} mantienen la copia local del plan
 *       ({@code subscription_projection}) con la que se aplica el límite de obras activas. Lee {@code plan},
 *       {@code status}, {@code maxActiveWorksites} y {@code validUntil}.</li>
 * </ul>
 */
@Component
public class OrganizationEventConsumer {

    /** Valor de {@code processed_events.consumer}. */
    public static final String CONSUMER = "OrganizationEventConsumer";

    static final String ORGANIZATION_REGISTERED = "OrganizationRegistered";
    static final String USER_CREATED = "UserCreated";
    static final String USER_UPDATED = "UserUpdated";
    static final Set<String> SUBSCRIPTION_STATE_EVENTS = Set.of("SubscriptionActivated", "SubscriptionExpired",
            "SubscriptionBillingPeriodChangeScheduled");

    private static final Logger log = LoggerFactory.getLogger(OrganizationEventConsumer.class);

    private final IdempotentEventProcessor processor;
    private final OrganizationCommandService organizations;
    private final JdbcUserProjection users;
    private final JdbcSubscriptionProjection subscriptions;

    public OrganizationEventConsumer(IdempotentEventProcessor processor, OrganizationCommandService organizations,
            JdbcUserProjection users, JdbcSubscriptionProjection subscriptions) {
        this.processor = processor;
        this.organizations = organizations;
        this.users = users;
        this.subscriptions = subscriptions;
    }

    @KafkaListener(topics = "${buildshield.messaging.consumes.iam:buildshield.iam}")
    public void onIamMessage(ConsumerRecord<String, String> record) {
        processor.process(record, CONSUMER, this::handleIam);
    }

    @KafkaListener(topics = "${buildshield.messaging.consumes.subscription:buildshield.subscription}")
    public void onSubscriptionMessage(ConsumerRecord<String, String> record) {
        processor.process(record, CONSUMER, this::handleSubscription);
    }

    void handleIam(EventEnvelope event) {
        JsonNode payload = event.payload();
        switch (event.eventType()) {
            case ORGANIZATION_REGISTERED -> {
                UUID organizationId = payload.hasNonNull("organizationId")
                        ? UUID.fromString(payload.get("organizationId").asText()) : event.organizationId();
                if (organizations.registerFromIam(organizationId, text(payload, "ruc"), text(payload, "legalName"))) {
                    log.info("Organización {} registrada", organizationId);
                }
            }
            case USER_CREATED, USER_UPDATED -> users.upsert(event.organizationId(),
                    UUID.fromString(payload.get("userId").asText()), text(payload, "fullName"), text(payload, "email"),
                    payload.path("role").asText(), payload.path("active").asBoolean(true));
            default -> log.debug("Evento {} ignorado por organization", event.eventType());
        }
    }

    void handleSubscription(EventEnvelope event) {
        if (!SUBSCRIPTION_STATE_EVENTS.contains(event.eventType())) {
            log.debug("Evento {} ignorado por organization", event.eventType());
            return;
        }
        JsonNode payload = event.payload();
        UUID organizationId = payload.hasNonNull("organizationId")
                ? UUID.fromString(payload.get("organizationId").asText()) : event.organizationId();
        String status = "SubscriptionExpired".equals(event.eventType()) ? "EXPIRED"
                : payload.path("status").asText("ACTIVE");
        Integer maxActiveWorksites = payload.path("maxActiveWorksites").isInt()
                ? payload.get("maxActiveWorksites").asInt() : null;
        Instant validUntil = payload.hasNonNull("validUntil") ? Instant.parse(payload.get("validUntil").asText()) : null;
        Instant occurredAt = event.occurredAt() == null ? Instant.EPOCH : event.occurredAt();
        boolean applied = subscriptions.apply(organizationId, payload.path("plan").asText("FREE"), status,
                maxActiveWorksites, validUntil, occurredAt);
        log.info("Plan de la organización {}: {} {} (límite {}){}", organizationId, payload.path("plan").asText(),
                status, maxActiveWorksites == null ? "sin límite" : maxActiveWorksites,
                applied ? "" : " ignorado: llegó después de uno más nuevo");
    }

    private static String text(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.get(field).asText() : null;
    }
}
