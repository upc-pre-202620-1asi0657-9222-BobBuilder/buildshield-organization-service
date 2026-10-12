package pe.buildshield.organization.domain.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Obras de la organización del contexto. */
public interface WorksiteRepository {

    Optional<Worksite> findById(UUID id);

    List<Worksite> findAll();

    List<Worksite> findAllById(Collection<UUID> ids);

    /** Obras de la organización activas en esa fecha (sin fecha de fin o con fin no vencido). */
    long countActive(LocalDate day);

    Worksite save(Worksite worksite);
}
