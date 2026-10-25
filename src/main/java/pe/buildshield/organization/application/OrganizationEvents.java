package pe.buildshield.organization.application;

import pe.buildshield.organization.domain.model.Material;
import pe.buildshield.organization.domain.model.StaffAssignment;
import pe.buildshield.organization.domain.model.Warehouse;
import pe.buildshield.organization.domain.model.Worksite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Eventos de integración que publica organization en el tópico {@code buildshield.organization} (payload
 * del sobre). Los nombres de los campos son el contrato con los demás servicios: no se renombran. Los campos
 * después del mínimo del contrato son adicionales.
 */
public final class OrganizationEvents {

    public static final String LOCATION_REGISTERED = "LocationRegistered";
    public static final String LOCATION_UPDATED = "LocationUpdated";
    public static final String MATERIAL_REGISTERED = "MaterialRegistered";
    public static final String MATERIAL_UPDATED = "MaterialUpdated";
    public static final String STAFF_ASSIGNED = "StaffAssigned";
    public static final String STAFF_UNASSIGNED = "StaffUnassigned";

    /** Tipo de agregado de cada evento en el sobre; {@code outbox_events} no lo guarda, se deduce del evento. */
    public static final String LOCATION = "Location";
    public static final String MATERIAL = "Material";
    public static final String ASSIGNMENT = "Assignment";

    public static final String WORKSITE = "WORKSITE";
    public static final String WAREHOUSE = "WAREHOUSE";

    private OrganizationEvents() {
    }

    /**
     * Obra o almacén registrado o cambiado.
     *
     * @param type          WORKSITE o WAREHOUSE
     * @param active        almacén: no desactivado; obra: vigente hoy (sin fecha de fin o fin no vencido)
     * @param warehouseType solo almacenes: WAREHOUSE o COLLECTION_CENTER
     */
    public record LocationChanged(UUID locationId, String type, String name, String address, boolean active,
            String district, String city, String warehouseType, LocalDate startDate, LocalDate endDate) {

        static LocationChanged of(Worksite worksite, LocalDate today) {
            boolean active = worksite.endDate() == null || !worksite.endDate().isBefore(today);
            return new LocationChanged(worksite.id(), WORKSITE, worksite.name(), worksite.location().address(), active,
                    worksite.location().district(), worksite.location().city(), null, worksite.startDate(),
                    worksite.endDate());
        }

        static LocationChanged of(Warehouse warehouse) {
            return new LocationChanged(warehouse.id(), WAREHOUSE, warehouse.name(), warehouse.address(),
                    warehouse.active(), null, null, warehouse.type().name(), null, null);
        }
    }

    /** Material del catálogo registrado o cambiado. */
    public record MaterialChanged(UUID materialId, String sku, String name, String unit, BigDecimal unitWeightKg,
            BigDecimal tolerancePercent, boolean active) {

        static MaterialChanged of(Material material) {
            return new MaterialChanged(material.id(), material.sku().value(), material.name(), material.unit().name(),
                    material.unitWeightKg(), material.wasteTolerance().percent(), material.active());
        }
    }

    /** Encargado asignado a una obra o almacén, o asignación terminada. */
    public record StaffAssignmentChanged(UUID assignmentId, UUID userId, UUID locationId, String locationType,
            String role) {

        static StaffAssignmentChanged of(StaffAssignment assignment) {
            return new StaffAssignmentChanged(assignment.id(), assignment.userId(), assignment.siteId(),
                    assignment.siteType().name(), assignment.siteType().assignableRole());
        }
    }
}
