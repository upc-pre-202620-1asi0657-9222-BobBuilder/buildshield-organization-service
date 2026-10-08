package pe.buildshield.organization.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.buildshield.organization.application.OrganizationService;
import pe.buildshield.organization.infrastructure.persistence.JdbcUserProjection;
import pe.buildshield.organization.shared.messaging.EventEnvelope;
import pe.buildshield.organization.shared.messaging.IdempotentEventProcessor;

import java.util.UUID;

/**
 * Consume el tópico {@code buildshield.iam}:
 *
 * <ul>
 *   <li>{@code OrganizationRegistered}: crea la fila de {@code organizations}.</li>
 *   <li>{@code UserCreated} y {@code UserUpdated}: actualizan la copia local de usuarios
 *       ({@code user_projection}), que valida el rol al asignar encargados (US17) y bloquea tokens de usuarios
 *       desactivados.</li>
 * </ul>
 *
 * Cada evento se procesa una sola vez ({@link IdempotentEventProcessor}). Los demás tipos se ignoran.
 */
@Component
public class IamEventConsumer {

    static final String ORGANIZATION_REGISTERED = "OrganizationRegistered";
    static final String USER_CREATED = "UserCreated";
    static final String USER_UPDATED = "UserUpdated";

    private static final Logger log = LoggerFactory.getLogger(IamEventConsumer.class);

    private final IdempotentEventProcessor processor;
    private final OrganizationService organizations;
    private final JdbcUserProjection users;

    public IamEventConsumer(IdempotentEventProcessor processor, OrganizationService organizations,
            JdbcUserProjection users) {
        this.processor = processor;
        this.organizations = organizations;
        this.users = users;
    }

    @KafkaListener(topics = "${buildshield.messaging.consumes.iam:buildshield.iam}")
    public void onMessage(ConsumerRecord<String, String> record) {
        processor.process(record, this::handle);
    }

    void handle(EventEnvelope event) {
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

    private static String text(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.get(field).asText() : null;
    }
}
