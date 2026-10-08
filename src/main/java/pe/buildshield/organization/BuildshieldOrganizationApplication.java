package pe.buildshield.organization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Organización y Configuración de BuildShield (microservicio, puerto 8082, base organization_db). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BuildshieldOrganizationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuildshieldOrganizationApplication.class, args);
    }
}
