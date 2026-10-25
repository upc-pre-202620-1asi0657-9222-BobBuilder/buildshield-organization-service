package pe.buildshield.organization.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.organization.shared.error.ResourceNotFoundException;
import pe.buildshield.organization.shared.messaging.OutboxWriter;
import pe.buildshield.organization.domain.model.Warehouse;
import pe.buildshield.organization.domain.model.WarehouseRepository;
import pe.buildshield.organization.domain.model.WarehouseType;

import java.util.List;
import java.util.UUID;

/**
 * US15: almacenes y centros de acopio; se desactivan y reactivan, nunca se borran. Cada cambio publica
 * {@code LocationRegistered} o {@code LocationUpdated} (tipo WAREHOUSE) por el outbox.
 */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouses;
    private final SiteVisibility visibility;

    private final OutboxWriter outbox;

    public WarehouseService(WarehouseRepository warehouses, SiteVisibility visibility, OutboxWriter outbox) {
        this.warehouses = warehouses;
        this.visibility = visibility;
        this.outbox = outbox;
    }

    @Transactional
    public WarehouseView register(RegisterWarehouse command) {
        Warehouse warehouse = Warehouse.register(command.name(), command.type(), command.address());
        return WarehouseView.of(publish(OrganizationEvents.LOCATION_REGISTERED, warehouses.save(warehouse)));
    }

    @Transactional(readOnly = true)
    public WarehouseView get(UUID id) {
        return WarehouseView.of(warehouses.findById(id).filter(visibility::canSee).orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public List<WarehouseView> list() {
        return visibility.visibleWarehouses().stream().map(WarehouseView::of).toList();
    }

    @Transactional
    public WarehouseView update(UUID id, UpdateWarehouse command) {
        Warehouse warehouse = warehouses.findById(id).orElseThrow(() -> notFound(id));
        warehouse.update(command.name(), command.type(), command.address());
        if (Boolean.FALSE.equals(command.active())) {
            warehouse.deactivate();
        } else if (Boolean.TRUE.equals(command.active())) {
            warehouse.activate();
        }
        return WarehouseView.of(publish(OrganizationEvents.LOCATION_UPDATED, warehouses.save(warehouse)));
    }

    private Warehouse publish(String eventType, Warehouse warehouse) {
        outbox.append(eventType, warehouse.id(),
                OrganizationEvents.LocationChanged.of(warehouse));
        return warehouse;
    }

    static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "El almacén " + id + " no existe");
    }

    public record RegisterWarehouse(String name, WarehouseType type, String address) {
    }

    /** Campos nulos: no cambian. {@code active=false} desactiva, {@code true} reactiva. */
    public record UpdateWarehouse(String name, WarehouseType type, String address, Boolean active) {
    }

    public record WarehouseView(UUID id, String name, WarehouseType type, String address, boolean active) {

        static WarehouseView of(Warehouse warehouse) {
            return new WarehouseView(warehouse.id(), warehouse.name(), warehouse.type(), warehouse.address(),
                    warehouse.active());
        }
    }
}
