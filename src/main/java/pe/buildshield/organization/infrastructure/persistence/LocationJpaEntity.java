package pe.buildshield.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.buildshield.organization.domain.model.Location;
import pe.buildshield.organization.domain.model.Warehouse;
import pe.buildshield.organization.domain.model.WarehouseType;
import pe.buildshield.organization.domain.model.Worksite;
import pe.buildshield.organization.shared.persistence.AuditableAbstractAggregateRoot;

import java.time.LocalDate;

/**
 * Tabla locations: obras (type WORKSITE) y almacenes (type WAREHOUSE) en una sola tabla, como en el ER v4.
 * Los agregados {@link Worksite} y {@link Warehouse} siguen separados en el dominio.
 */
@Entity
@Table(name = "locations")
public class LocationJpaEntity extends AuditableAbstractAggregateRoot<LocationJpaEntity> {

    static final String WORKSITE = "WORKSITE";
    static final String WAREHOUSE = "WAREHOUSE";

    @Column(name = "type", nullable = false, updatable = false, length = 10)
    private String type;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_type", length = 20)
    private WarehouseType warehouseType;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    protected LocationJpaEntity() {
    }

    static LocationJpaEntity of(Worksite worksite, LocalDate today) {
        LocationJpaEntity entity = new LocationJpaEntity();
        entity.type = WORKSITE;
        entity.apply(worksite, today);
        return entity;
    }

    static LocationJpaEntity of(Warehouse warehouse) {
        LocationJpaEntity entity = new LocationJpaEntity();
        entity.type = WAREHOUSE;
        entity.apply(warehouse);
        return entity;
    }

    /** {@code active} de una obra es su vigencia al momento del cambio (sin fin o con fin no vencido). */
    void apply(Worksite worksite, LocalDate today) {
        name = worksite.name();
        address = worksite.location().address();
        district = worksite.location().district();
        city = worksite.location().city();
        latitude = worksite.location().latitude();
        longitude = worksite.location().longitude();
        startDate = worksite.startDate();
        plannedEndDate = worksite.endDate();
        active = worksite.activeOn(today);
    }

    void apply(Warehouse warehouse) {
        name = warehouse.name();
        warehouseType = warehouse.type();
        address = warehouse.address();
        active = warehouse.active();
    }

    Worksite toWorksite() {
        return Worksite.restore(getId(), name, new Location(address, district, city, latitude, longitude), startDate,
                plannedEndDate, getVersion());
    }

    Warehouse toWarehouse() {
        return Warehouse.restore(getId(), name, warehouseType, address, active, getVersion());
    }
}
