package pe.buildshield.organization.support;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** Limpieza SOLO de la base efímera de pruebas ({@code test} de Testcontainers o {@code organization_it}). */
public final class TestDatabase {

    /** Tablas del servicio, sin el historial de Flyway. */
    public static final List<String> TABLES = List.of("location_assignments", "materials", "locations",
            "organizations", "user_projection", "subscription_projection", "outbox_events",
            "processed_events");

    private TestDatabase() {
    }

    public static void reset(JdbcTemplate jdbc) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String database;
            try (var query = connection.createStatement(); var result = query.executeQuery("SELECT current_database()")) {
                result.next();
                database = result.getString(1);
            }
            if (!(database.equals("test") || database.equals("organization_it"))) {
                throw new IllegalStateException("La limpieza requiere una base temporal de pruebas");
            }
            try (var statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE " + String.join(", ", TABLES) + " CASCADE");
            }
            return null;
        });
    }
}
