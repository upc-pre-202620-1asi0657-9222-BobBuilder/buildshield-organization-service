package pe.buildshield.organization.application;

import org.junit.jupiter.api.Test;
import pe.buildshield.organization.domain.model.Location;
import pe.buildshield.organization.domain.model.Material;
import pe.buildshield.organization.domain.model.SiteType;
import pe.buildshield.organization.domain.model.Sku;
import pe.buildshield.organization.domain.model.StaffAssignment;
import pe.buildshield.organization.domain.model.UnitOfMeasure;
import pe.buildshield.organization.domain.model.Warehouse;
import pe.buildshield.organization.domain.model.WarehouseType;
import pe.buildshield.organization.domain.model.WasteTolerance;
import pe.buildshield.organization.domain.model.Worksite;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Payloads de los eventos de integración con los campos mínimos del contrato. */
class OrganizationEventsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Test
    void a_worksite_is_a_worksite_location_active_until_its_end_date() {
        Worksite open = worksite(null);
        Worksite ending = worksite(TODAY);
        Worksite finished = worksite(TODAY.minusDays(1));

        OrganizationEvents.LocationChanged event = OrganizationEvents.LocationChanged.of(open, TODAY);

        assertThat(event.locationId()).isEqualTo(open.id());
        assertThat(event.type()).isEqualTo("WORKSITE");
        assertThat(event.name()).isEqualTo("Torre Norte");
        assertThat(event.address()).isEqualTo("Av. Javier Prado 123");
        assertThat(event.district()).isEqualTo("San Isidro");
        assertThat(event.active()).isTrue();
        assertThat(OrganizationEvents.LocationChanged.of(ending, TODAY).active()).isTrue();
        assertThat(OrganizationEvents.LocationChanged.of(finished, TODAY).active()).isFalse();
    }

    @Test
    void a_warehouse_is_a_warehouse_location_with_its_state() {
        Warehouse warehouse = Warehouse.restore(UUID.randomUUID(), "Almacén Central", WarehouseType.COLLECTION_CENTER,
                "Av. Argentina 2500", false, 3L);

        OrganizationEvents.LocationChanged event = OrganizationEvents.LocationChanged.of(warehouse);

        assertThat(event.type()).isEqualTo("WAREHOUSE");
        assertThat(event.warehouseType()).isEqualTo("COLLECTION_CENTER");
        assertThat(event.active()).isFalse();
        assertThat(event.address()).isEqualTo("Av. Argentina 2500");
    }

    @Test
    void a_material_carries_sku_unit_and_tolerance() {
        Material material = Material.restore(UUID.randomUUID(), new Sku("CEM-001"), "Cemento", UnitOfMeasure.BAG,
                new WasteTolerance(new BigDecimal("2.5")), true, 0L);

        OrganizationEvents.MaterialChanged event = OrganizationEvents.MaterialChanged.of(material);

        assertThat(event.materialId()).isEqualTo(material.id());
        assertThat(event.sku()).isEqualTo("CEM-001");
        assertThat(event.unit()).isEqualTo("BAG");
        assertThat(event.tolerancePercent()).isEqualByComparingTo("2.5");
        assertThat(event.active()).isTrue();
    }

    @Test
    void an_assignment_carries_the_user_and_the_location() {
        UUID user = UUID.randomUUID();
        UUID site = UUID.randomUUID();
        StaffAssignment assignment = StaffAssignment.restore(UUID.randomUUID(), user, SiteType.WAREHOUSE, site,
                Instant.now(), null, 0L);

        OrganizationEvents.StaffAssignmentChanged event = OrganizationEvents.StaffAssignmentChanged.of(assignment);

        assertThat(event.assignmentId()).isEqualTo(assignment.id());
        assertThat(event.userId()).isEqualTo(user);
        assertThat(event.locationId()).isEqualTo(site);
        assertThat(event.locationType()).isEqualTo("WAREHOUSE");
    }

    private static Worksite worksite(LocalDate end) {
        return Worksite.restore(UUID.randomUUID(), "Torre Norte",
                new Location("Av. Javier Prado 123", "San Isidro", "Lima", null, null), LocalDate.of(2026, 1, 1), end, 0L);
    }
}
