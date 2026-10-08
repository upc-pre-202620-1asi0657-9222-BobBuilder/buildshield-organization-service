package pe.buildshield.organization.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que organization necesita saber de un usuario para asignarlo (US17). Lo implementa la copia local de
 * los usuarios de iam ({@code user_projection}, alimentada por {@code UserCreated} y {@code UserUpdated}).
 */
public interface StaffDirectory {

    /** Usuario de la organización del contexto; vacío si no existe (o aún no llegó su evento) o es de otra. */
    Optional<StaffMember> findMember(UUID userId);

    /**
     * @param role nombre del rol en iam (ADMINISTRATOR, WAREHOUSE_MANAGER, SITE_MANAGER)
     */
    record StaffMember(UUID userId, String role, boolean active) {
    }
}
