package pe.buildshield.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El filtro multiempresa deja solo los lugares de la organización del contexto; {@code type} separa obras y almacenes. */
interface SpringDataLocationRepository extends JpaRepository<LocationJpaEntity, UUID> {

    Optional<LocationJpaEntity> findByIdAndType(UUID id, String type);

    List<LocationJpaEntity> findAllByTypeOrderByName(String type);

    List<LocationJpaEntity> findAllByTypeAndActiveTrueOrderByName(String type);

    List<LocationJpaEntity> findAllByTypeAndIdIn(String type, Collection<UUID> ids);

    @Query("SELECT count(l) FROM LocationJpaEntity l WHERE l.type = 'WORKSITE' "
            + "AND (l.plannedEndDate IS NULL OR l.plannedEndDate >= :day)")
    long countActiveWorksites(@Param("day") LocalDate day);
}
