package pe.buildshield.organization.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.buildshield.organization.application.SubscriptionPlans;
import pe.buildshield.organization.shared.tenant.TenantContext;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Copia local del plan de cada organización ({@code subscription_projection}), alimentada por los eventos de
 * {@code buildshield.subscription}. Un evento más antiguo que el último aplicado no la cambia (los eventos de una
 * misma suscripción van en orden por partición, pero un reproceso no debe retroceder el estado).
 */
@Component
public class JdbcSubscriptionProjection implements SubscriptionPlans {

    private static final String UPSERT = """
            INSERT INTO subscription_projection (organization_id, plan, status, max_active_worksites, valid_until,
                event_occurred_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (organization_id) DO UPDATE SET plan = EXCLUDED.plan, status = EXCLUDED.status,
                max_active_worksites = EXCLUDED.max_active_worksites, valid_until = EXCLUDED.valid_until,
                event_occurred_at = EXCLUDED.event_occurred_at, updated_at = EXCLUDED.updated_at
            WHERE subscription_projection.event_occurred_at <= EXCLUDED.event_occurred_at
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcSubscriptionProjection(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<PlanLimits> current() {
        return find(TenantContext.require().organizationId());
    }

    /** Plan de una organización (para el filtro de solo lectura, que ya tiene la organización del token). */
    public Optional<PlanLimits> find(UUID organizationId) {
        return jdbc.query("SELECT plan, status, max_active_worksites FROM subscription_projection "
                        + "WHERE organization_id = ?",
                (rs, row) -> new PlanLimits(rs.getString("plan"), rs.getString("status"),
                        (Integer) rs.getObject("max_active_worksites")), organizationId).stream().findFirst();
    }

    @Override
    public void lockActiveWorksiteQuota() {
        UUID organizationId = TenantContext.require().organizationId();
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, organizationId.toString());
    }

    /**
     * Aplica el estado que trae un evento de la suscripción.
     *
     * @return {@code true} si cambió la copia (no era un evento más antiguo que el último aplicado)
     */
    public boolean apply(UUID organizationId, String plan, String status, Integer maxActiveWorksites,
            Instant validUntil, Instant occurredAt) {
        return jdbc.update(UPSERT, organizationId, plan, status, maxActiveWorksites,
                validUntil == null ? null : Timestamp.from(validUntil), Timestamp.from(occurredAt),
                Timestamp.from(clock.instant())) == 1;
    }
}
