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

    private <T> T in(TenantInfo tenant, Supplier<T> work) {
        return TenantContext.callAs(tenant, () -> tx.execute(status -> work.get()));
    }

    private static Material cement() {
        return Material.register(new Sku("CEM-001"), "Cemento", UnitOfMeasure.BAG, new WasteTolerance(new BigDecimal("2.5")));
    }
}
