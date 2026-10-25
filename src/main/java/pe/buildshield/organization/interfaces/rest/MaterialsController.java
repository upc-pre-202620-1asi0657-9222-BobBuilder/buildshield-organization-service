package pe.buildshield.organization.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.organization.shared.error.ErrorResponse;
import pe.buildshield.organization.application.OrganizationCommandService;
import pe.buildshield.organization.application.OrganizationQueryService;
import pe.buildshield.organization.application.MaterialService;
import pe.buildshield.organization.application.MaterialService.MaterialView;
import pe.buildshield.organization.domain.model.UnitOfMeasure;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/materials")
@Tag(name = "Materiales", description = "US16: catálogo de materiales con SKU único por organización y tolerancia de merma")
@SecurityRequirement(name = "bearer")
class MaterialsController {

    private final OrganizationCommandService commands;
    private final OrganizationQueryService queries;

    MaterialsController(OrganizationCommandService commands, OrganizationQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Agregar un material al catálogo",
            description = "SKU único en la organización, nombre, unidad de medida y tolerancia de merma.")
    @ApiResponse(responseCode = "201", description = "Material registrado",
            content = @Content(schema = @Schema(implementation = MaterialResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.MATERIAL)))
    @ApiResponse(responseCode = "400", description = "SKU, unidad o tolerancia inválidos (la tolerancia va de 0 a 100 %)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Solo el administrador registra materiales",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Ya existe un material con ese SKU en la organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<MaterialResource> register(@Valid @RequestBody RegisterMaterialRequest request) {
        MaterialView created = commands.registerMaterial(new MaterialService.RegisterMaterial(
                request.sku(), request.name(), request.unit(), request.wasteTolerancePercent(), request.unitWeightKg()));
        return ResponseEntity.created(URI.create("/api/v1/materials/" + created.id())).body(MaterialResource.of(created));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar el catálogo", description = "Todos los roles; los encargados ven solo los materiales activos.")
    @ApiResponse(responseCode = "200", description = "Materiales del catálogo",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MaterialResource.class)),
                    examples = @ExampleObject(value = OrganizationApiExamples.MATERIALS)))
    List<MaterialResource> list() {
        return queries.materials().stream().map(MaterialResource::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar un material", description = "Datos de un material del catálogo de la organización.")
    @ApiResponse(responseCode = "200", description = "Material",
            content = @Content(schema = @Schema(implementation = MaterialResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.MATERIAL)))
    @ApiResponse(responseCode = "404", description = "No existe o es de otra organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    MaterialResource get(@Parameter(description = "Id del material", example = OrganizationApiExamples.MATERIAL_ID) @PathVariable UUID id) {
        return MaterialResource.of(queries.material(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Modificar un material", description = "Solo cambian los campos enviados; el SKU no se modifica.")
    @ApiResponse(responseCode = "200", description = "Material modificado",
            content = @Content(schema = @Schema(implementation = MaterialResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.MATERIAL)))
    @ApiResponse(responseCode = "404", description = "No existe o es de otra organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    MaterialResource update(@Parameter(description = "Id del material", example = OrganizationApiExamples.MATERIAL_ID) @PathVariable UUID id, @Valid @RequestBody UpdateMaterialRequest request) {
        return MaterialResource.of(commands.updateMaterial(id, new MaterialService.UpdateMaterial(
                request.name(), request.unit(), request.wasteTolerancePercent(), request.unitWeightKg(), request.active())));
    }

    @Schema(description = "Datos del material")
    record RegisterMaterialRequest(
            @Schema(description = "Único en la organización; no distingue mayúsculas", example = "CEM-001")
            @NotBlank @Size(max = 40) String sku,
            @Schema(example = "Cemento Portland tipo I") @NotBlank @Size(max = 120) String name,
            @Schema(example = "BAG") @NotNull UnitOfMeasure unit,
            @Schema(description = "Porcentaje de merma aceptable, de 0 a 100, hasta 2 decimales", example = "2.5")
            @NotNull BigDecimal wasteTolerancePercent,
            @Schema(description = "Peso de una unidad en kg (0 a 9999999.999, hasta 3 decimales); sin valor queda en 0",
                    example = "42.500")
            BigDecimal unitWeightKg) {
    }

    @Schema(description = "Cambios del material; los campos ausentes no cambian")
    record UpdateMaterialRequest(
            @Size(max = 120) String name,
            UnitOfMeasure unit,
            BigDecimal wasteTolerancePercent,
            @Schema(description = "Peso de una unidad en kg", example = "42.500") BigDecimal unitWeightKg,
            @Schema(description = "false lo retira del catálogo de los encargados") Boolean active) {
    }

    @Schema(description = "Material del catálogo")
    record MaterialResource(UUID id, String sku, String name, UnitOfMeasure unit, BigDecimal wasteTolerancePercent,
            BigDecimal unitWeightKg, boolean active) {

        static MaterialResource of(MaterialView view) {
            return new MaterialResource(view.id(), view.sku(), view.name(), view.unit(), view.wasteTolerancePercent(),
                    view.unitWeightKg(), view.active());
        }
    }
}
