package pe.buildshield.organization.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import pe.buildshield.organization.application.AssignmentService;
import pe.buildshield.organization.application.MaterialService;
import pe.buildshield.organization.application.WarehouseService;
import pe.buildshield.organization.application.WorksiteService;
import pe.buildshield.organization.domain.model.Location;
import pe.buildshield.organization.domain.model.SiteType;
import pe.buildshield.organization.domain.model.UnitOfMeasure;
import pe.buildshield.organization.domain.model.WarehouseType;
import pe.buildshield.organization.shared.messaging.Topics;
import pe.buildshield.organization.shared.tenant.TenantContext;
import pe.buildshield.organization.shared.tenant.TenantInfo;
import pe.buildshield.organization.support.OrganizationIntegrationTest;
import pe.buildshield.organization.support.TestDatabase;
import pe.buildshield.organization.support.TestOrganizations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Eventos de organization contra Kafka embebido y PostgreSQL real: lo que publica el outbox en
 * {@code buildshield.organization} y lo que consume de {@code buildshield.iam}, una sola vez por evento.
 */
@OrganizationIntegrationTest
class OrganizationMessagingIT {

    @Autowired
    WorksiteService worksites;

    @Autowired
    WarehouseService warehouses;

    @Autowired
    MaterialService materials;

    @Autowired
    AssignmentService assignments;

    @Autowired
    OrganizationOutboxPublisher publisher;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @BeforeEach
    void clean() {
        TestDatabase.reset(jdbc);
    }

    @Test
    void catalog_changes_reach_the_organization_topic_with_the_common_envelope() throws Exception {
        TenantInfo admin = TestOrganizations.register(jdbc, "20123456789", "Constructora Andina SAC");
        UUID rosa = TestOrganizations.addUser(jdbc, admin.organizationId(), "rosa@andina.pe", "WAREHOUSE_MANAGER");

        UUID[] ids = TenantContext.callAs(admin, () -> {
            UUID worksite = worksites.register(new WorksiteService.RegisterWorksite("Torre Norte",
                    new Location("Av. Javier Prado 123", "San Isidro", "Lima", null, null), LocalDate.of(2026, 11, 1),
                    null)).id();
            UUID warehouse = warehouses.register(new WarehouseService.RegisterWarehouse("Almacén Central",
                    WarehouseType.WAREHOUSE, "Av. Argentina 2500")).id();
            warehouses.update(warehouse, new WarehouseService.UpdateWarehouse(null, null, null, false));
            warehouses.update(warehouse, new WarehouseService.UpdateWarehouse(null, null, null, true));
            UUID material = materials.register(new MaterialService.RegisterMaterial("CEM-001", "Cemento",
                    UnitOfMeasure.BAG, new BigDecimal("2.5"))).id();
            UUID assignment = assignments.assign(new AssignmentService.AssignStaff(rosa, SiteType.WAREHOUSE,
                    warehouse)).id();
            assignments.update(assignment, new AssignmentService.UpdateAssignment(false));
            return new UUID[]{worksite, warehouse, material, assignment};
        });

        assertThat(publisher.publishPending()).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class))
                .isZero();

        List<JsonNode> events = readOrganizationTopic(admin.organizationId(), 7);
        assertThat(events).extracting(event -> event.get("eventType").asText()).containsExactlyInAnyOrder(
                "LocationRegistered", "LocationRegistered", "LocationUpdated", "LocationUpdated",
                "MaterialRegistered", "StaffAssigned", "StaffUnassigned");
        JsonNode worksite = find(events, "LocationRegistered", ids[0]);
        assertThat(worksite.get("aggregateType").asText()).isEqualTo("LOCATION");
        assertThat(worksite.get("version").asInt()).isEqualTo(1);
        assertThat(worksite.get("actorId").asText()).isEqualTo(admin.userId().toString());
        assertThat(worksite.at("/payload/type").asText()).isEqualTo("WORKSITE");
        assertThat(worksite.at("/payload/name").asText()).isEqualTo("Torre Norte");
        assertThat(worksite.at("/payload/address").asText()).isEqualTo("Av. Javier Prado 123");
        assertThat(worksite.at("/payload/active").asBoolean()).isTrue();
        assertThat(worksite.at("/payload/startDate").asText()).isEqualTo("2026-11-01");
        assertThat(find(events, "LocationRegistered", ids[1]).at("/payload/type").asText()).isEqualTo("WAREHOUSE");
        JsonNode material = find(events, "MaterialRegistered", ids[2]);
        assertThat(material.at("/payload/sku").asText()).isEqualTo("CEM-001");
        assertThat(material.at("/payload/unit").asText()).isEqualTo("BAG");
        assertThat(material.at("/payload/tolerancePercent").decimalValue()).isEqualByComparingTo("2.5");
        JsonNode unassigned = find(events, "StaffUnassigned", ids[3]);
        assertThat(unassigned.at("/payload/userId").asText()).isEqualTo(rosa.toString());
        assertThat(unassigned.at("/payload/locationId").asText()).isEqualTo(ids[1].toString());
        assertThat(unassigned.at("/payload/locationType").asText()).isEqualTo("WAREHOUSE");
    }

    @Test
    void iam_events_create_the_organization_and_the_user_copy_once() {
        UUID organizationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String registered = TestOrganizations.organizationRegistered(json, organizationId, "20123456789",
                "Constructora Andina SAC", adminId, "ana@andina.pe");

        kafka.send(Topics.IAM, organizationId.toString(), registered);
        kafka.send(Topics.IAM, organizationId.toString(), registered);
        kafka.send(Topics.IAM, adminId.toString(), TestOrganizations.userEvent(json, "UserCreated", organizationId,
                adminId, "Ana Torres", "ana@andina.pe", "ADMINISTRATOR", true));
        kafka.send(Topics.IAM, adminId.toString(), TestOrganizations.userEvent(json, "UserUpdated", organizationId,
                adminId, "Ana Torres", "ana@andina.pe", "SITE_MANAGER", false));

        await().atMost(Duration.ofSeconds(30)).until(() -> Boolean.FALSE.equals(jdbc.query(
                "SELECT active FROM user_projection WHERE user_id = ?", rs -> rs.next() ? rs.getBoolean(1) : null,
                adminId)));
        await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM organizations WHERE id = ?", Long.class, organizationId) == 1);
        assertThat(jdbc.queryForMap("SELECT ruc, legal_name FROM organizations WHERE id = ?", organizationId))
                .containsEntry("ruc", "20123456789")
                .containsEntry("legal_name", "Constructora Andina SAC");
        assertThat(jdbc.queryForMap("SELECT organization_id, role, email, full_name FROM user_projection WHERE user_id = ?",
                adminId))
                .containsEntry("organization_id", organizationId)
                .containsEntry("role", "SITE_MANAGER")
                .containsEntry("email", "ana@andina.pe")
                .containsEntry("full_name", "Ana Torres");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Long.class)).isEqualTo(3);
    }

    @Test
    void subscription_events_keep_the_local_plan_copy_and_an_old_event_does_not_win() {
        UUID organizationId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();

        kafka.send(Topics.SUBSCRIPTION, organizationId.toString(), TestOrganizations.subscriptionEvent(json,
                "SubscriptionActivated", organizationId, "PLUS", "ACTIVE", 5, now));
        kafka.send(Topics.SUBSCRIPTION, organizationId.toString(), TestOrganizations.subscriptionEvent(json,
                "SubscriptionActivated", organizationId, "FREE", "TRIAL", null, now.minusSeconds(3600)));
        kafka.send(Topics.SUBSCRIPTION, organizationId.toString(), TestOrganizations.subscriptionEvent(json,
                "SubscriptionCheckoutAbandoned", organizationId, "PRO", "ACTIVE", null, now.plusSeconds(1)));

        await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM processed_events", Long.class) == 3);
        assertThat(jdbc.queryForMap("SELECT plan, status, max_active_worksites FROM subscription_projection "
                + "WHERE organization_id = ?", organizationId))
                .containsEntry("plan", "PLUS").containsEntry("status", "ACTIVE")
                .containsEntry("max_active_worksites", 5);

        kafka.send(Topics.SUBSCRIPTION, organizationId.toString(), TestOrganizations.subscriptionEvent(json,
                "SubscriptionExpired", organizationId, "PLUS", "ACTIVE", 5, now.plusSeconds(60)));
        await().atMost(Duration.ofSeconds(30)).until(() -> "EXPIRED".equals(jdbc.queryForObject(
                "SELECT status FROM subscription_projection WHERE organization_id = ?", String.class, organizationId)));
    }

    private static JsonNode find(List<JsonNode> events, String type, UUID aggregateId) {
        return events.stream()
                .filter(event -> event.get("eventType").asText().equals(type)
                        && event.get("aggregateId").asText().equals(aggregateId.toString()))
                .findFirst().orElseThrow(() -> new AssertionError(type + " de " + aggregateId));
    }

    private List<JsonNode> readOrganizationTopic(UUID organizationId, int expected) throws Exception {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "organization-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<JsonNode> found = new ArrayList<>();
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer()) {
            consumer.subscribe(List.of(Topics.ORGANIZATION));
            long deadline = System.currentTimeMillis() + 20_000;
            while (found.size() < expected && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    JsonNode envelope = json.readTree(record.value());
                    if (envelope.get("organizationId").asText().equals(organizationId.toString())) {
                        assertThat(record.key()).isEqualTo(envelope.get("aggregateId").asText());
                        found.add(envelope);
                    }
                }
            }
        }
        assertThat(found).hasSize(expected);
        return found;
    }
}
