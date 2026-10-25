package pe.buildshield.organization.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.organization.application.OrganizationQueryService;
import pe.buildshield.organization.application.OrganizationService;
import pe.buildshield.organization.shared.error.ErrorResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizaciones", description = "La organización (empresa cliente) del usuario")
@SecurityRequirement(name = "bearer")
class OrganizationsController {

    private final OrganizationQueryService queries;

    OrganizationsController(OrganizationQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ver la organización propia",
            description = "La organización del token. Llega desde iam con OrganizationRegistered: justo después del "
                    + "alta puede tardar menos de un segundo en estar disponible.")
    @ApiResponse(responseCode = "200", description = "Organización del usuario",
            content = @Content(schema = @Schema(implementation = OrganizationResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.ORGANIZATION)))
    @ApiResponse(responseCode = "404", description = "La organización aún no llegó desde iam",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    OrganizationResource current() {
        return OrganizationResource.of(queries.currentOrganization());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ver una organización por id", description = "Solo la propia; otra se trata como inexistente.")
    @ApiResponse(responseCode = "200", description = "Organización del usuario",
            content = @Content(schema = @Schema(implementation = OrganizationResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.ORGANIZATION)))
    @ApiResponse(responseCode = "404", description = "No existe o es de otra organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    OrganizationResource get(@Parameter(description = "Id de la organización",
            example = "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b") @PathVariable UUID id) {
        return OrganizationResource.of(queries.organization(id));
    }

    @Schema(description = "Organización (empresa cliente)")
    record OrganizationResource(UUID id, String ruc, String legalName) {

        static OrganizationResource of(OrganizationService.OrganizationView view) {
            return new OrganizationResource(view.id(), view.ruc(), view.legalName());
        }
    }
}
