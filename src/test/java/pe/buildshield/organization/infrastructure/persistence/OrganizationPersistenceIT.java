package pe.buildshield.organization.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pe.buildshield.organization.shared.tenant.TenantContext;
import pe.buildshield.organization.shared.tenant.TenantInfo;
import pe.buildshield.organization.application.StaffDirectory;
import pe.buildshield.organization.application.SubscriptionPlans;
import pe.buildshield.organization.domain.model.Location;
import pe.buildshield.organization.domain.model.Material;
import pe.buildshield.organization.domain.model.MaterialRepository;
import pe.buildshield.organization.domain.model.SiteType;
import pe.buildshield.organization.domain.model.Sku;
import pe.buildshield.organization.domain.model.StaffAssignment;
import pe.buildshield.organization.domain.model.StaffAssignmentRepository;
import pe.buildshield.organization.domain.model.UnitOfMeasure;
import pe.buildshield.organization.domain.model.WasteTolerance;
import pe.buildshield.organization.domain.model.Worksite;
import pe.buildshield.organization.domain.model.WorksiteRepository;
import pe.buildshield.organization.support.OrganizationIntegrationTest;
import pe.buildshield.organization.support.TestDatabase;
import pe.buildshield.organization.support.TestOrganizations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Restricciones de la base que el servicio normalmente esconde (altas simultáneas) y el puerto
 * StaffDirectory implementado con la copia local de usuarios de iam, contra PostgreSQL real.
 */
@OrganizationIntegrationTest
class OrganizationPersistenceIT {

    @Autowired
    MaterialRepository materials;

    @Autowired
    WorksiteRepository worksites;

    @Autowired
    StaffAssignmentRepository assignments;

    @Autowired
    StaffDirectory staffDirectory;

    @Autowired
    JdbcSubscriptionProjection subscriptions;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private TenantInfo andina;
    private TenantInfo otra;

    @BeforeEach
    void twoOrganizations() {
        TestDatabase.reset(jdbc);
        andina = TestOrganizations.register(jdbc, "20123456789", "Andina");
        otra = TestOrganizations.register(jdbc, "20999999991", "Otra");
    }

    @Test
    void sku_is_unique_per_organization_in_the_database() {
        in(andina, () -> materials.save(cement()));
        in(otra, () -> materials.save(cement()));

        assertThatThrownBy(() -> in(andina, () -> materials.save(cement())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_materials_organization_sku");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM materials WHERE sku = 'CEM-001'", Long.class))
                .isEqualTo(2);
    }

    @Test
    void only_one_active_assignment_per_user_and_site_and_history_is_kept() {
        Worksite torre = in(andina, () -> worksites.save(Worksite.register("Torre",
                new Location("Av. 1", "Lince", "Lima", null, null), LocalDate.of(2026, 11, 1), null)));
        UUID jorge = UUID.randomUUID();
        StaffAssignment first = in(andina, () -> assignments.save(
                StaffAssignment.assign(jorge, "SITE_MANAGER", SiteType.WORKSITE, torre.id(), Instant.now())));

        assertThatThrownBy(() -> in(andina, () -> assignments.save(
                StaffAssignment.assign(jorge, "SITE_MANAGER", SiteType.WORKSITE, torre.id(), Instant.now()))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_staff_assignments_active_worksite");

        in(andina, () -> {
            first.end(Instant.now());
            return assignments.save(first);
        });
        in(andina, () -> assignments.save(
                StaffAssignment.assign(jorge, "SITE_MANAGER", SiteType.WORKSITE, torre.id(), Instant.now())));

        assertThat(in(andina, () -> assignments.findByUserId(jorge))).hasSize(2);
        assertThat(in(andina, () -> assignments.activeSiteIds(jorge, SiteType.WORKSITE))).containsExactly(torre.id());
        assertThat(in(andina, () -> assignments.activeSiteIds(jorge, SiteType.WAREHOUSE))).isEmpty();
    }

    @Test
    void staff_directory_finds_users_of_the_context_organization_only() {
        UUID ana = andina.userId();

        assertThat(in(andina, () -> staffDirectory.findMember(ana)))
                .hasValue(new StaffDirectory.StaffMember(ana, "ADMINISTRATOR", true));
        assertThat(in(otra, () -> staffDirectory.findMember(ana))).isEmpty();
    }

    @Test
    void materials_and_worksites_are_filtered_by_organization() {
        Material cement = in(andina, () -> materials.save(cement()));

        assertThat(in(otra, () -> materials.findById(cement.id()))).isEmpty();
        assertThat(in(otra, () -> materials.findBySku(new Sku("CEM-001")))).isEmpty();
        assertThat(in(andina, () -> materials.findBySku(new Sku("cem-001")))).isPresent();
    }

    @Test
    void only_worksites_without_end_date_or_not_finished_count_as_active() {
        LocalDate today = LocalDate.of(2026, 10, 11);
        Location lima = new Location("Av. Principal 100", "Miraflores", "Lima", null, null);
        in(andina, () -> worksites.save(Worksite.register("Abierta", lima, LocalDate.of(2026, 1, 1), null)));
        in(andina, () -> worksites.save(Worksite.register("Hasta hoy", lima, LocalDate.of(2026, 1, 1), today)));
        in(andina, () -> worksites.save(Worksite.register("Terminada", lima, LocalDate.of(2026, 1, 1),
                today.minusDays(1))));
        in(otra, () -> worksites.save(Worksite.register("De otra", lima, LocalDate.of(2026, 1, 1), null)));

        Long active = in(andina, () -> worksites.countActive(today));
        assertThat(active).isEqualTo(2L);
    }

    @Test
    void the_plan_copy_keeps_the_newest_event_and_the_quota_lock_works_inside_a_transaction() {
        Instant now = Instant.now();
        assertThat(subscriptions.apply(andina.organizationId(), "PLUS", "ACTIVE", 5, now, now)).isTrue();
        assertThat(subscriptions.apply(andina.organizationId(), "FREE", "TRIAL", null, null, now.minusSeconds(60)))
                .isFalse();

        var plan = in(andina, () -> subscriptions.current());
        assertThat(plan).contains(new SubscriptionPlans.PlanLimits("PLUS", "ACTIVE", 5));
        var none = in(otra, () -> subscriptions.current());
        assertThat(none).isEmpty();
        assertThat(subscriptions.apply(andina.organizationId(), "PLUS", "EXPIRED", 5, now, now.plusSeconds(1))).isTrue();
        assertThat(subscriptions.find(andina.organizationId()).orElseThrow().readOnly()).isTrue();
        Boolean locked = in(andina, () -> {
            subscriptions.lockActiveWorksiteQuota();
            return true;
        });
        assertThat(locked).isTrue();
    }

    private <T> T in(TenantInfo tenant, Supplier<T> work) {
        return TenantContext.callAs(tenant, () -> tx.execute(status -> work.get()));
    }

    private static Material cement() {
        return Material.register(new Sku("CEM-001"), "Cemento", UnitOfMeasure.BAG, new WasteTolerance(new BigDecimal("2.5")));
    }
}
