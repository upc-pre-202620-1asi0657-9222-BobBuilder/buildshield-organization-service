package pe.buildshield.organization.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.organization.domain.model.Organization;
import pe.buildshield.organization.domain.model.OrganizationRepository;
import pe.buildshield.organization.domain.model.Ruc;
import pe.buildshield.organization.shared.error.ResourceNotFoundException;
import pe.buildshield.organization.shared.tenant.TenantContext;

import java.util.UUID;

/**
 * La organización (empresa cliente). La registra iam en el alta (US01) y llega aquí con
 * {@code OrganizationRegistered}; el RUC único lo garantiza iam. Cada usuario consulta solo la suya.
 */
@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /**
     * Crea la fila de la organización informada por iam. Si ya existe (reentrega del evento) no hace nada.
     *
     * @return {@code true} si la creó
     */
    @Transactional
    public boolean registerFromIam(UUID organizationId, String ruc, String legalName) {
        if (organizations.findById(organizationId).isPresent()) {
            log.debug("La organización {} ya estaba registrada", organizationId);
            return false;
        }
        organizations.save(Organization.register(organizationId, new Ruc(ruc), legalName));
        return true;
    }

    /** La organización del token. */
    @Transactional(readOnly = true)
    public OrganizationView current() {
        UUID organizationId = TenantContext.require().organizationId();
        return get(organizationId);
    }

    /** Solo la propia organización: otra se trata como inexistente (404). */
    @Transactional(readOnly = true)
    public OrganizationView get(UUID organizationId) {
        if (!organizationId.equals(TenantContext.require().organizationId())) {
            throw notFound(organizationId);
        }
        return organizations.findById(organizationId).map(OrganizationView::of)
                .orElseThrow(() -> notFound(organizationId));
    }

    static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("ORGANIZATION_NOT_FOUND", "La organización " + id + " no existe");
    }

    public record OrganizationView(UUID id, String ruc, String legalName) {

        static OrganizationView of(Organization organization) {
            return new OrganizationView(organization.id(), organization.ruc().value(), organization.legalName());
        }
    }
}
