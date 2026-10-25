package pe.buildshield.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import pe.buildshield.organization.domain.model.SiteType;
import pe.buildshield.organization.domain.model.StaffAssignment;
import pe.buildshield.organization.shared.persistence.AuditableAbstractAggregateRoot;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabla location_assignments. El lugar es {@code location_id} (obra o almacén de {@code locations}) y {@code role} el
 * rol del encargado, que determina el tipo de lugar: SITE_MANAGER en obras y WAREHOUSE_MANAGER en almacenes.
 */
@Entity
@Table(name = "location_assignments")
public class LocationAssignmentJpaEntity extends AuditableAbstractAggregateRoot<LocationAssignmentJpaEntity> {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "role", nullable = false, updatable = false, length = 20)
    private String role;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected LocationAssignmentJpaEntity() {
    }

    LocationAssignmentJpaEntity(StaffAssignment assignment) {
        userId = assignment.userId();
        locationId = assignment.siteId();
        role = assignment.siteType().assignableRole();
        assignedAt = assignment.assignedAt();
        apply(assignment);
    }

    void apply(StaffAssignment assignment) {
        endedAt = assignment.endedAt();
    }

    UUID siteId() {
        return locationId;
    }

    SiteType siteType() {
        return SiteType.forRole(role);
    }

    StaffAssignment toDomain() {
        return StaffAssignment.restore(getId(), userId, siteType(), locationId, assignedAt, endedAt, getVersion());
    }
}
