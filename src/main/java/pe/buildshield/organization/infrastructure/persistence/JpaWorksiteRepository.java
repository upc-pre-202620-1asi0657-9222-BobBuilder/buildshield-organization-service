package pe.buildshield.organization.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.buildshield.organization.domain.model.Worksite;
import pe.buildshield.organization.domain.model.WorksiteRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaWorksiteRepository implements WorksiteRepository {

    private final SpringDataLocationRepository jpa;
    private final Clock clock;

    JpaWorksiteRepository(SpringDataLocationRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public Optional<Worksite> findById(UUID id) {
        return jpa.findByIdAndType(id, LocationJpaEntity.WORKSITE).map(LocationJpaEntity::toWorksite);
    }

    @Override
    public List<Worksite> findAll() {
        return jpa.findAllByTypeOrderByName(LocationJpaEntity.WORKSITE).stream().map(LocationJpaEntity::toWorksite)
                .toList();
    }

    @Override
    public List<Worksite> findAllById(Collection<UUID> ids) {
        return jpa.findAllByTypeAndIdIn(LocationJpaEntity.WORKSITE, ids).stream().map(LocationJpaEntity::toWorksite)
                .sorted(Comparator.comparing(Worksite::name)).toList();
    }

    @Override
    public long countActive(LocalDate day) {
        return jpa.countActiveWorksites(day);
    }

    @Override
    public Worksite save(Worksite worksite) {
        if (worksite.id() == null) {
            return jpa.saveAndFlush(LocationJpaEntity.of(worksite, today())).toWorksite();
        }
        LocationJpaEntity entity = Versions.load(jpa, worksite.id(), worksite.version(), LocationJpaEntity.class);
        entity.apply(worksite, today());
        return jpa.saveAndFlush(entity).toWorksite();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
