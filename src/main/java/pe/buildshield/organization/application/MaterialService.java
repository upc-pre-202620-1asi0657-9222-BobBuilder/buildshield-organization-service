package pe.buildshield.organization.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.organization.shared.error.ConflictException;
import pe.buildshield.organization.shared.error.ResourceNotFoundException;
import pe.buildshield.organization.shared.messaging.OutboxWriter;
import pe.buildshield.organization.domain.model.Material;
import pe.buildshield.organization.domain.model.MaterialRepository;
import pe.buildshield.organization.domain.model.Sku;
import pe.buildshield.organization.domain.model.UnitOfMeasure;
import pe.buildshield.organization.domain.model.UnitWeight;
import pe.buildshield.organization.domain.model.WasteTolerance;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * US16: catálogo de materiales con SKU único por organización y tolerancia de merma. Cada cambio publica
 * {@code MaterialRegistered} o {@code MaterialUpdated} por el outbox.
 */
@Service
public class MaterialService {

    public static final String SKU_ALREADY_EXISTS = "SKU_ALREADY_EXISTS";

    private final MaterialRepository materials;
    private final SiteVisibility visibility;

    private final OutboxWriter outbox;

    public MaterialService(MaterialRepository materials, SiteVisibility visibility, OutboxWriter outbox) {
        this.materials = materials;
        this.visibility = visibility;
        this.outbox = outbox;
    }

    @Transactional
    public MaterialView register(RegisterMaterial command) {
        Sku sku = new Sku(command.sku());
        Material material = Material.register(sku, command.name(), command.unit(),
                new WasteTolerance(command.wasteTolerancePercent()), unitWeight(command.unitWeightKg()));
        if (materials.existsBySku(sku)) {
            throw skuAlreadyExists(sku);
        }
        try {
            return MaterialView.of(publish(OrganizationEvents.MATERIAL_REGISTERED, materials.save(material)));
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_materials_organization_sku")) {
                throw skuAlreadyExists(sku);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public MaterialView get(UUID id) {
        return MaterialView.of(materials.findById(id).filter(visibility::canSee).orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public List<MaterialView> list() {
        return visibility.visibleMaterials().stream().map(MaterialView::of).toList();
    }

    @Transactional
    public MaterialView update(UUID id, UpdateMaterial command) {
        Material material = materials.findById(id).orElseThrow(() -> notFound(id));
        material.update(command.name(), command.unit(),
                command.wasteTolerancePercent() == null ? null : new WasteTolerance(command.wasteTolerancePercent()),
                command.unitWeightKg() == null ? null : new UnitWeight(command.unitWeightKg()), command.active());
        return MaterialView.of(publish(OrganizationEvents.MATERIAL_UPDATED, materials.save(material)));
    }

    private Material publish(String eventType, Material material) {
        outbox.append(eventType, material.id(), OrganizationEvents.MaterialChanged.of(material));
        return material;
    }

    /** Sin peso informado queda en cero: los clientes anteriores al peso unitario siguen registrando materiales. */
    private static UnitWeight unitWeight(BigDecimal kg) {
        return kg == null ? UnitWeight.ZERO : new UnitWeight(kg);
    }

    private static ConflictException skuAlreadyExists(Sku sku) {
        return new ConflictException(SKU_ALREADY_EXISTS, "Ya existe un material con el SKU " + sku.value());
    }

    static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("MATERIAL_NOT_FOUND", "El material " + id + " no existe");
    }

    /** {@code unitWeightKg} nulo: cero. */
    public record RegisterMaterial(String sku, String name, UnitOfMeasure unit, BigDecimal wasteTolerancePercent,
            BigDecimal unitWeightKg) {

        public RegisterMaterial(String sku, String name, UnitOfMeasure unit, BigDecimal wasteTolerancePercent) {
            this(sku, name, unit, wasteTolerancePercent, null);
        }
    }

    /** Campos nulos: no cambian. El SKU no se modifica. */
    public record UpdateMaterial(String name, UnitOfMeasure unit, BigDecimal wasteTolerancePercent,
            BigDecimal unitWeightKg, Boolean active) {

        public UpdateMaterial(String name, UnitOfMeasure unit, BigDecimal wasteTolerancePercent, Boolean active) {
            this(name, unit, wasteTolerancePercent, null, active);
        }
    }

    public record MaterialView(UUID id, String sku, String name, UnitOfMeasure unit, BigDecimal wasteTolerancePercent,
            BigDecimal unitWeightKg, boolean active) {

        static MaterialView of(Material material) {
            return new MaterialView(material.id(), material.sku().value(), material.name(), material.unit(),
                    material.wasteTolerance().percent(), material.unitWeightKg(), material.active());
        }
    }
}
