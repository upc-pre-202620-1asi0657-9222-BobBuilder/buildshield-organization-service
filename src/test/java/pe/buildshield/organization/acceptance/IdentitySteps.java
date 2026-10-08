package pe.buildshield.organization.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import pe.buildshield.organization.shared.messaging.Topics;
import pe.buildshield.organization.shared.security.JwtTokenIssuer;
import pe.buildshield.organization.shared.tenant.TenantInfo;
import pe.buildshield.organization.support.TestOrganizations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Pasos de identidad que en el Core resolvía iam. Aquí la organización y los usuarios llegan como en producción:
 * {@code OrganizationRegistered}, {@code UserCreated} y {@code UserUpdated} en {@code buildshield.iam}. El
 * "inicio de sesión" emite el JWT con la clave de prueba, igual que lo haría iam.
 */
public class IdentitySteps {

    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    ScenarioSession session;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtTokenIssuer issuer;

    @Autowired
    ObjectMapper json;

    /** Usuarios del escenario por correo. */
    private final Map<String, TenantInfo> users = new HashMap<>();

    private UUID organizationId;

    @Dado("que la organización {string} con RUC {string} está registrada con el administrador {string} y contraseña {string}")
    public void organizationRegistered(String legalName, String ruc, String adminEmail, String password) {
        organizationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        kafka.send(Topics.IAM, organizationId.toString(),
                TestOrganizations.organizationRegistered(json, organizationId, ruc, legalName, adminId, adminEmail));
        userCreated(adminId, "Administrador", adminEmail, "ADMINISTRATOR");
        await().atMost(EVENT_TIMEOUT).until(() -> count("SELECT count(*) FROM organizations WHERE id = ?",
                organizationId) == 1);
    }

    @Dado("que creé el usuario {string} con correo {string}, rol {string} y contraseña {string}")
    public void userCreated(String fullName, String email, String role, String password) {
        userCreated(UUID.randomUUID(), fullName, email, role(role));
    }

    @Dado("que desactivaron al usuario {string}")
    public void userDeactivated(String email) {
        TenantInfo user = user(email);
        kafka.send(Topics.IAM, user.userId().toString(), TestOrganizations.userEvent(json, "UserUpdated",
                user.organizationId(), user.userId(), null, email, user.role(), false));
        await().atMost(EVENT_TIMEOUT).until(() -> count(
                "SELECT count(*) FROM user_projection WHERE user_id = ? AND NOT active", user.userId()) == 1);
    }

    @Dado("que {string} inició sesión con la contraseña {string}")
    public void signedIn(String email, String password) {
        session.accessToken(issuer.issue(user(email)).value());
    }

    @Entonces("la respuesta tiene estado {int}")
    public void responseStatus(int status) {
        session.expectStatus(status);
    }

    @Entonces("la operación es rechazada con estado {int}")
    public void rejected(int status) {
        session.expectStatus(status);
    }

    @Entonces("la operación es rechazada con estado {int} y código {string}")
    public void rejectedWithCode(int status, String code) throws Exception {
        session.expectStatus(status);
        assertThat(session.body().path("code").asText()).isEqualTo(code);
    }

    private void userCreated(UUID userId, String fullName, String email, String role) {
        assertThat(organizationId).as("primero se registra la organización").isNotNull();
        kafka.send(Topics.IAM, userId.toString(), TestOrganizations.userEvent(json, "UserCreated", organizationId,
                userId, fullName, email, role, true));
        users.put(email, new TenantInfo(organizationId, userId, role));
        session.put(email, userId);
        await().atMost(EVENT_TIMEOUT).until(() -> count("SELECT count(*) FROM user_projection WHERE user_id = ?",
                userId) == 1);
    }

    private TenantInfo user(String email) {
        TenantInfo user = users.get(email);
        assertThat(user).as("el usuario %s no existe en el escenario", email).isNotNull();
        return user;
    }

    private long count(String sql, UUID id) {
        Long count = jdbc.queryForObject(sql, Long.class, id);
        return count == null ? 0 : count;
    }

    /** "encargado de obra" → SITE_MANAGER; "encargado de almacén" → WAREHOUSE_MANAGER. */
    private static String role(String displayName) {
        return switch (displayName.trim().toLowerCase()) {
            case "administrador" -> "ADMINISTRATOR";
            case "encargado de obra" -> "SITE_MANAGER";
            case "encargado de almacén", "encargado de almacen" -> "WAREHOUSE_MANAGER";
            default -> displayName;
        };
    }
}
