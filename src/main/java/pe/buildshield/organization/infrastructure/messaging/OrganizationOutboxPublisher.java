package pe.buildshield.organization.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.buildshield.organization.application.OrganizationEvents;
import pe.buildshield.organization.shared.config.BuildshieldProperties;
import pe.buildshield.organization.shared.messaging.OutboxPublisher;

import java.time.Clock;

/**
 * Productor Kafka de organization: cada 500 ms envía al tópico {@code buildshield.organization} los eventos
 * pendientes del outbox ({@code LocationRegistered}, {@code LocationUpdated}, {@code MaterialRegistered},
 * {@code MaterialUpdated}, {@code StaffAssigned}, {@code StaffUnassigned}).
 */
@Component
public class OrganizationOutboxPublisher extends OutboxPublisher {

    public OrganizationOutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, ObjectMapper json,
            PlatformTransactionManager transactionManager, Clock clock, BuildshieldProperties properties) {
        super(jdbc, kafka, json, new TransactionTemplate(transactionManager), clock,
                properties.getMessaging().getTopic(), properties.getMessaging().getBatchSize(),
                properties.getMessaging().getSendTimeout());
    }

    /** Obras y almacenes son {@code Location}; los eventos de personal, de una {@code Assignment}. */
    @Override
    protected String aggregateTypeOf(String eventType) {
        return switch (eventType) {
            case OrganizationEvents.LOCATION_REGISTERED, OrganizationEvents.LOCATION_UPDATED -> OrganizationEvents.LOCATION;
            case OrganizationEvents.MATERIAL_REGISTERED, OrganizationEvents.MATERIAL_UPDATED -> OrganizationEvents.MATERIAL;
            case OrganizationEvents.STAFF_ASSIGNED, OrganizationEvents.STAFF_UNASSIGNED -> OrganizationEvents.ASSIGNMENT;
            default -> super.aggregateTypeOf(eventType);
        };
    }

    @Scheduled(fixedDelayString = "${buildshield.messaging.outbox-interval:PT0.5S}")
    public void publish() {
        publishPending();
    }
}
