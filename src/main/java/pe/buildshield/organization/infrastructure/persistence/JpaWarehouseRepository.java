package pe.buildshield.organization.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.buildshield.organization.domain.model.Warehouse;
import pe.buildshield.organization.domain.model.WarehouseRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaWarehouseRepository implements WarehouseRepository {

    private final SpringDataLocationRepository jpa;

    JpaWarehouseRepository(SpringDataLocationRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Warehouse> findById(UUID id) {
        return jpa.findByIdAndType(id, LocationJpaEntity.WAREHOUSE).map(LocationJpaEntity::toWarehouse);
    }

    @Override
    public List<Warehouse> findAll() {
        return jpa.findAllByTypeOrderByName(LocationJpaEntity.WAREHOUSE).stream().map(LocationJpaEntity::toWarehouse)
                .toList();
    }

    @Override
    public List<Warehouse> findAllActive() {
        return jpa.findAllByTypeAndActiveTrueOrderByName(LocationJpaEntity.WAREHOUSE).stream()
                .map(LocationJpaEntity::toWarehouse).toList();
    }

    @Override
    public List<Warehouse> findAllById(Collection<UUID> ids) {
        return jpa.findAllByTypeAndIdIn(LocationJpaEntity.WAREHOUSE, ids).stream().map(LocationJpaEntity::toWarehouse)
                .sorted(Comparator.comparing(Warehouse::name)).toList();
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        if (warehouse.id() == null) {
            return jpa.saveAndFlush(LocationJpaEntity.of(warehouse)).toWarehouse();
        }
        LocationJpaEntity entity = Versions.load(jpa, warehouse.id(), warehouse.version(), LocationJpaEntity.class);
        entity.apply(warehouse);
        return jpa.saveAndFlush(entity).toWarehouse();
    }
}
