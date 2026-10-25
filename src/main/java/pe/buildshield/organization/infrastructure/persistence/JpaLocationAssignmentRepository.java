package pe.buildshield.organization.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.buildshield.organization.domain.model.SiteType;
import pe.buildshield.organization.domain.model.StaffAssignment;
import pe.buildshield.organization.domain.model.StaffAssignmentRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
class JpaLocationAssignmentRepository implements StaffAssignmentRepository {

    private final SpringDataLocationAssignmentRepository jpa;

    JpaLocationAssignmentRepository(SpringDataLocationAssignmentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<StaffAssignment> findById(UUID id) {
        return jpa.findById(id).map(LocationAssignmentJpaEntity::toDomain);
    }

    @Override
    public List<StaffAssignment> findAll() {
        return jpa.findAllByOrderByAssignedAt().stream().map(LocationAssignmentJpaEntity::toDomain).toList();
    }

    @Override
    public List<StaffAssignment> findByUserId(UUID userId) {
        return jpa.findAllByUserIdOrderByAssignedAt(userId).stream().map(LocationAssignmentJpaEntity::toDomain).toList();
    }

    @Override
    public boolean existsActive(UUID userId, UUID siteId) {
        return jpa.existsActive(userId, siteId);
    }

    @Override
    public Set<UUID> activeSiteIds(UUID userId, SiteType siteType) {
        return jpa.findAllByUserIdAndEndedAtIsNull(userId).stream()
                .filter(assignment -> assignment.siteType() == siteType)
                .map(LocationAssignmentJpaEntity::siteId)
                .collect(Collectors.toSet());
    }

    @Override
    public StaffAssignment save(StaffAssignment assignment) {
        if (assignment.id() == null) {
            return jpa.saveAndFlush(new LocationAssignmentJpaEntity(assignment)).toDomain();
        }
        LocationAssignmentJpaEntity entity = Versions.load(jpa, assignment.id(), assignment.version(),
                LocationAssignmentJpaEntity.class);
        entity.apply(assignment);
        return jpa.saveAndFlush(entity).toDomain();
    }
}
