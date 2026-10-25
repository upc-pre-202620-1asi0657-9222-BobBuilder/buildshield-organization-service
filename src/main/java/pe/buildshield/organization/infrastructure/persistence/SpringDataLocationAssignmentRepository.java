package pe.buildshield.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface SpringDataLocationAssignmentRepository extends JpaRepository<LocationAssignmentJpaEntity, UUID> {

    List<LocationAssignmentJpaEntity> findAllByOrderByAssignedAt();

    List<LocationAssignmentJpaEntity> findAllByUserIdOrderByAssignedAt(UUID userId);

    List<LocationAssignmentJpaEntity> findAllByUserIdAndEndedAtIsNull(UUID userId);

    @Query("""
            SELECT COUNT(a) > 0 FROM LocationAssignmentJpaEntity a
            WHERE a.userId = :userId AND a.endedAt IS NULL AND a.locationId = :siteId
            """)
    boolean existsActive(UUID userId, UUID siteId);
}
