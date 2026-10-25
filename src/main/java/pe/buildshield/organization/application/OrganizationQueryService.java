package pe.buildshield.organization.application;

import org.springframework.stereotype.Service;
import pe.buildshield.organization.application.AssignmentService.AssignmentView;
import pe.buildshield.organization.application.MaterialService.MaterialView;
import pe.buildshield.organization.application.OrganizationService.OrganizationView;
import pe.buildshield.organization.application.WarehouseService.WarehouseView;
import pe.buildshield.organization.application.WorksiteService.WorksiteView;

import java.util.List;
import java.util.UUID;

/**
 * Consultas de organization (componente Organization QueryService del C4): la organización, las obras, los
 * almacenes, los materiales y las asignaciones del personal. Cada consulta devuelve solo lo que el rol de quien
 * pregunta puede ver dentro de su organización.
 */
@Service
public class OrganizationQueryService {

    private final OrganizationService organizations;
    private final WorksiteService worksites;
    private final WarehouseService warehouses;
    private final MaterialService materials;
    private final AssignmentService assignments;

    public OrganizationQueryService(OrganizationService organizations, WorksiteService worksites,
            WarehouseService warehouses, MaterialService materials, AssignmentService assignments) {
        this.organizations = organizations;
        this.worksites = worksites;
        this.warehouses = warehouses;
        this.materials = materials;
        this.assignments = assignments;
    }

    /** La organización del token. */
    public OrganizationView currentOrganization() {
        return organizations.current();
    }

    public OrganizationView organization(UUID id) {
        return organizations.get(id);
    }

    public WorksiteView worksite(UUID id) {
        return worksites.get(id);
    }

    public List<WorksiteView> worksites() {
        return worksites.list();
    }

    public WarehouseView warehouse(UUID id) {
        return warehouses.get(id);
    }

    public List<WarehouseView> warehouses() {
        return warehouses.list();
    }

    public MaterialView material(UUID id) {
        return materials.get(id);
    }

    public List<MaterialView> materials() {
        return materials.list();
    }

    public AssignmentView assignment(UUID id) {
        return assignments.get(id);
    }

    public List<AssignmentView> assignments() {
        return assignments.list();
    }
}
