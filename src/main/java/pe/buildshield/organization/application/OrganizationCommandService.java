package pe.buildshield.organization.application;

import org.springframework.stereotype.Service;
import pe.buildshield.organization.application.AssignmentService.AssignStaff;
import pe.buildshield.organization.application.AssignmentService.AssignmentView;
import pe.buildshield.organization.application.AssignmentService.UpdateAssignment;
import pe.buildshield.organization.application.MaterialService.MaterialView;
import pe.buildshield.organization.application.MaterialService.RegisterMaterial;
import pe.buildshield.organization.application.MaterialService.UpdateMaterial;
import pe.buildshield.organization.application.WarehouseService.RegisterWarehouse;
import pe.buildshield.organization.application.WarehouseService.UpdateWarehouse;
import pe.buildshield.organization.application.WarehouseService.WarehouseView;
import pe.buildshield.organization.application.WorksiteService.RegisterWorksite;
import pe.buildshield.organization.application.WorksiteService.UpdateWorksite;
import pe.buildshield.organization.application.WorksiteService.WorksiteView;

import java.util.UUID;

/**
 * Casos de uso que cambian estado en organization (componente Organization CommandService del C4): registrar la
 * organización que informa iam, crear y editar obras, almacenes y materiales, y asignar o terminar la asignación del
 * personal. Es la fachada que usan los controladores y el consumidor de eventos; cada caso lo resuelve el servicio de
 * su agregado, que publica el evento por el outbox en la misma transacción.
 */
@Service
public class OrganizationCommandService {

    private final OrganizationService organizations;
    private final WorksiteService worksites;
    private final WarehouseService warehouses;
    private final MaterialService materials;
    private final AssignmentService assignments;

    public OrganizationCommandService(OrganizationService organizations, WorksiteService worksites,
            WarehouseService warehouses, MaterialService materials, AssignmentService assignments) {
        this.organizations = organizations;
        this.worksites = worksites;
        this.warehouses = warehouses;
        this.materials = materials;
        this.assignments = assignments;
    }

    /** @return {@code true} si creó la organización; {@code false} si ya existía (reentrega del evento) */
    public boolean registerFromIam(UUID organizationId, String ruc, String legalName) {
        return organizations.registerFromIam(organizationId, ruc, legalName);
    }

    public WorksiteView registerWorksite(RegisterWorksite command) {
        return worksites.register(command);
    }

    public WorksiteView updateWorksite(UUID id, UpdateWorksite command) {
        return worksites.update(id, command);
    }

    public WarehouseView registerWarehouse(RegisterWarehouse command) {
        return warehouses.register(command);
    }

    public WarehouseView updateWarehouse(UUID id, UpdateWarehouse command) {
        return warehouses.update(id, command);
    }

    public MaterialView registerMaterial(RegisterMaterial command) {
        return materials.register(command);
    }

    public MaterialView updateMaterial(UUID id, UpdateMaterial command) {
        return materials.update(id, command);
    }

    public AssignmentView assignStaff(AssignStaff command) {
        return assignments.assign(command);
    }

    /** Termina la asignación ({@code active=false}). */
    public AssignmentView updateAssignment(UUID id, UpdateAssignment command) {
        return assignments.update(id, command);
    }
}
