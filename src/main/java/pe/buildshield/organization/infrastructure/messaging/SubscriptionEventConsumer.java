package pe.buildshield.organization.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.buildshield.organization.infrastructure.persistence.JdbcSubscriptionProjection;
import pe.buildshield.organization.shared.messaging.EventEnvelope;
import pe.buildshield.organization.shared.messaging.IdempotentEventProcessor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Consume {@code buildshield.subscription} y mantiene la copia local del plan ({@code subscription_projection}):
 * {@code SubscriptionActivated} (prueba, pago o renovación), {@code SubscriptionExpired} (cuenta en solo lectura) y
 * {@code SubscriptionBillingPeriodChangeScheduled}. Lee {@code plan}, {@code status}, {@code maxActiveWorksites} y
 * {@code validUntil}. Cada evento se procesa una sola vez ({@link IdempotentEventProcessor}); los demás tipos se
 * ignoran.
 */
@Component
public class SubscriptionEventConsumer {

    static final Set<String> SUBSCRIPTION_STATE_EVENTS = Set.of("SubscriptionActivated", "SubscriptionExpired",
            "SubscriptionBillingPeriodChangeScheduled");

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    private final IdempotentEventProcessor processor;
    private final JdbcSubscriptionProjection projection;

    public SubscriptionEventConsumer(IdempotentEventProcessor processor, JdbcSubscriptionProjection projection) {
        this.processor = processor;
        this.projection = projection;
    }

    @KafkaListener(topics = "${buildshield.messaging.consumes.subscription:buildshield.subscription}")
    public void onMessage(ConsumerRecord<String, String> record) {
        processor.process(record, this::handle);
    }

    void handle(EventEnvelope event) {
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
        boolean applied = projection.apply(organizationId, payload.path("plan").asText("FREE"), status,
                maxActiveWorksites, validUntil, occurredAt);
        log.info("Plan de la organización {}: {} {} (límite {}){}", organizationId, payload.path("plan").asText(),
                status, maxActiveWorksites == null ? "sin límite" : maxActiveWorksites,
                applied ? "" : " ignorado: llegó después de uno más nuevo");
    }
}
