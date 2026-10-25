package pe.buildshield.organization.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.buildshield.organization.application.StaffDirectory;
import pe.buildshield.organization.shared.tenant.TenantContext;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Copia local de los usuarios de iam ({@code user_projection}), alimentada por {@code UserCreated} y
 * {@code UserUpdated}. Las lecturas filtran por la organización del {@code TenantContext}.
 */
@Component
public class JdbcUserProjection implements StaffDirectory {

    private static final String UPSERT = "INSERT INTO user_projection "
            + "(user_id, organization_id, full_name, email, role, active, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (user_id) DO UPDATE SET "
            + "full_name = COALESCE(EXCLUDED.full_name, user_projection.full_name), "
            + "email = COALESCE(EXCLUDED.email, user_projection.email), "
            + "role = EXCLUDED.role, active = EXCLUDED.active, updated_at = EXCLUDED.updated_at";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcUserProjection(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<StaffMember> findMember(UUID userId) {
        UUID organizationId = TenantContext.require().organizationId();
        return jdbc.query("SELECT user_id, role, active FROM user_projection WHERE user_id = ? AND organization_id = ?",
                (rs, row) -> new StaffMember(rs.getObject("user_id", UUID.class), rs.getString("role"),
                        rs.getBoolean("active")), userId, organizationId).stream().findFirst();
    }

    /** Alta o cambio del usuario. El nombre y el correo nulos conservan el valor anterior. */
    public void upsert(UUID organizationId, UUID userId, String fullName, String email, String role, boolean active) {
        jdbc.update(UPSERT, userId, organizationId, fullName, email, role, active, Timestamp.from(clock.instant()));
    }
}
