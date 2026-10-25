package pe.buildshield.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.buildshield.organization.shared.persistence.AuditableAbstractAggregateRoot;
import pe.buildshield.organization.domain.model.Material;
import pe.buildshield.organization.domain.model.Sku;
import pe.buildshield.organization.domain.model.UnitOfMeasure;
import pe.buildshield.organization.domain.model.UnitWeight;
import pe.buildshield.organization.domain.model.WasteTolerance;

import java.math.BigDecimal;

@Entity
@Table(name = "materials")
public class MaterialJpaEntity extends AuditableAbstractAggregateRoot<MaterialJpaEntity> {

    @Column(name = "sku", nullable = false, length = 40, updatable = false)
    private String sku;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 10)
    private UnitOfMeasure unit;

    @Column(name = "unit_weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal unitWeightKg;

    @Column(name = "tolerance_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal tolerancePct;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected MaterialJpaEntity() {
    }

    MaterialJpaEntity(Material material) {
        sku = material.sku().value();
        apply(material);
    }

    void apply(Material material) {
        name = material.name();
        unit = material.unit();
        unitWeightKg = material.unitWeightKg();
        tolerancePct = material.wasteTolerance().percent();
        active = material.active();
    }

    Material toDomain() {
        return Material.restore(getId(), new Sku(sku), name, unit, new WasteTolerance(tolerancePct), new UnitWeight(unitWeightKg), active,
                getVersion());
    }
}
