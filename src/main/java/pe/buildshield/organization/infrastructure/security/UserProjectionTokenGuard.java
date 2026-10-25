package pe.buildshield.organization.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.buildshield.organization.shared.security.RevokedTokenStore;

import java.util.UUID;

/**
 * Revocación de tokens para el filtro JWT del kernel compartido. La lista de tokens cerrados con sign-out vive
 * en iam (este servicio no la ve: el token vence en 15 minutos). Lo que sí se valida aquí, con la copia local
 * de usuarios, es que el usuario siga activo y con el rol del token (US04, defensa en profundidad). Si la copia
 * aún no tiene al usuario, no se bloquea.
 */
@Component
class UserProjectionTokenGuard implements RevokedTokenStore {

    private final JdbcTemplate jdbc;

    UserProjectionTokenGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isRevoked(String tokenId) {
        return false;
    }

    @Override
    public boolean isUserBlocked(UUID userId, String role) {
        if (userId == null) {
            return false;
        }
        Boolean blocked = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_projection WHERE user_id = ? AND (NOT active OR role <> ?))",
                Boolean.class, userId, role == null ? "" : role);
        return Boolean.TRUE.equals(blocked);
    }
}
