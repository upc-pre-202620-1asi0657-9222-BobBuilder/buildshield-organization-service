package pe.buildshield.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

interface SpringDataWorksiteRepository extends JpaRepository<WorksiteJpaEntity, UUID> {

    /** El filtro multiempresa deja solo las obras de la organización del contexto. */
    @Query("SELECT count(w) FROM WorksiteJpaEntity w WHERE w.endDate IS NULL OR w.endDate >= :day")
    long countActive(@Param("day") LocalDate day);
}
