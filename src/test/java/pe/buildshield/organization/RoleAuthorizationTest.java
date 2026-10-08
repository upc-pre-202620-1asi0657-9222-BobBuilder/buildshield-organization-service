package pe.buildshield.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pe.buildshield.organization.application.AssignmentService;
import pe.buildshield.organization.application.MaterialService;
import pe.buildshield.organization.application.OrganizationService;
import pe.buildshield.organization.application.WarehouseService;
import pe.buildshield.organization.application.WorksiteService;
import pe.buildshield.organization.domain.model.Location;
import pe.buildshield.organization.domain.model.SiteType;
import pe.buildshield.organization.domain.model.UnitOfMeasure;
import pe.buildshield.organization.domain.model.WarehouseType;
import pe.buildshield.organization.shared.security.JwtTokenIssuer;
import pe.buildshield.organization.shared.security.RevokedTokenStore;
import pe.buildshield.organization.shared.tenant.TenantInfo;
import pe.buildshield.organization.support.WebSliceTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de autorización de toda la API de organization: cada endpoint contra usuario anónimo y cada rol
 * (los servicios están simulados). Además verifica que ningún endpoint quede sin una regla explícita
 * de {@code @PreAuthorize}. Portada de la matriz del Core (filas de organización).
 */
@WebMvcTest
@WebSliceTest
class RoleAuthorizationTest {

    private static final UUID ANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static final Map<String, String> BODIES = Map.ofEntries(
            Map.entry("worksite", "{\"name\":\"Torre\",\"address\":\"Av. 1\",\"district\":\"Lince\",\"city\":\"Lima\",\"startDate\":\"2026-11-01\"}"),
            Map.entry("patch-worksite", "{\"name\":\"Torre 2\"}"),
            Map.entry("warehouse", "{\"name\":\"Central\",\"type\":\"WAREHOUSE\",\"address\":\"Av. 1\"}"),
            Map.entry("deactivate", "{\"active\":false}"),
            Map.entry("material", "{\"sku\":\"CEM-001\",\"name\":\"Cemento\",\"unit\":\"BAG\",\"wasteTolerancePercent\":2.5}"),
            Map.entry("patch-material", "{\"wasteTolerancePercent\":1.5}"),
            Map.entry("assignment", "{\"userId\":\"00000000-0000-0000-0000-0000000000bb\",\"siteType\":\"WORKSITE\",\"siteId\":\"00000000-0000-0000-0000-0000000000aa\"}"),
            Map.entry("end-assignment", "{\"active\":false}"));

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtTokenIssuer issuer;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    RevokedTokenStore revokedTokens;

    @MockitoBean
    OrganizationService organizationService;

    @MockitoBean
    WorksiteService worksiteService;

    @MockitoBean
    WarehouseService warehouseService;

    @MockitoBean
    MaterialService materialService;

    @MockitoBean
    AssignmentService assignmentService;

    @BeforeEach
    void stubServices() {
        OrganizationService.OrganizationView organization = new OrganizationService.OrganizationView(ANY_ID,
                "20123456789", "Andina");
        when(organizationService.current()).thenReturn(organization);
        when(organizationService.get(any())).thenReturn(organization);
        WorksiteService.WorksiteView worksite = new WorksiteService.WorksiteView(ANY_ID, "Torre",
                new Location("Av. 1", "Lince", "Lima", null, null), LocalDate.of(2026, 11, 1), null);
        when(worksiteService.register(any())).thenReturn(worksite);
        when(worksiteService.update(any(), any())).thenReturn(worksite);
        when(worksiteService.get(any())).thenReturn(worksite);
        when(worksiteService.list()).thenReturn(List.of(worksite));
        WarehouseService.WarehouseView warehouse = new WarehouseService.WarehouseView(ANY_ID, "Central",
                WarehouseType.WAREHOUSE, "Av. 1", true);
        when(warehouseService.register(any())).thenReturn(warehouse);
        when(warehouseService.update(any(), any())).thenReturn(warehouse);
        when(warehouseService.get(any())).thenReturn(warehouse);
        when(warehouseService.list()).thenReturn(List.of(warehouse));
        MaterialService.MaterialView material = new MaterialService.MaterialView(ANY_ID, "CEM-001", "Cemento",
                UnitOfMeasure.BAG, new BigDecimal("2.50"), true);
        when(materialService.register(any())).thenReturn(material);
        when(materialService.update(any(), any())).thenReturn(material);
        when(materialService.get(any())).thenReturn(material);
        when(materialService.list()).thenReturn(List.of(material));
        AssignmentService.AssignmentView assignment = new AssignmentService.AssignmentView(ANY_ID, ANY_ID,
                SiteType.WORKSITE, ANY_ID, Instant.now(), null, true);
        when(assignmentService.assign(any())).thenReturn(assignment);
        when(assignmentService.update(any(), any())).thenReturn(assignment);
        when(assignmentService.get(any())).thenReturn(assignment);
        when(assignmentService.list()).thenReturn(List.of(assignment));
    }

    @ParameterizedTest(name = "{0} {1} como {3} -> {4}")
    @CsvSource({
            // endpoint, cuerpo, quién, estado
            "GET  , /api/v1/organizations/me, , ANONYMOUS, 401",
            "GET  , /api/v1/organizations/me, , ADMINISTRATOR, 200",
            "GET  , /api/v1/organizations/me, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/organizations/me, , SITE_MANAGER, 200",
            "GET  , /api/v1/organizations/00000000-0000-0000-0000-0000000000aa, , ANONYMOUS, 401",
            "GET  , /api/v1/organizations/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "POST , /api/v1/worksites, worksite, ANONYMOUS, 401",
            "POST , /api/v1/worksites, worksite, ADMINISTRATOR, 201",
            "POST , /api/v1/worksites, worksite, WAREHOUSE_MANAGER, 403",
            "POST , /api/v1/worksites, worksite, SITE_MANAGER, 403",
            "GET  , /api/v1/worksites, , ANONYMOUS, 401",
            "GET  , /api/v1/worksites, , ADMINISTRATOR, 200",
            "GET  , /api/v1/worksites, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/worksites, , SITE_MANAGER, 200",
            "GET  , /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "GET  , /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "PATCH, /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, patch-worksite, ANONYMOUS, 401",
            "PATCH, /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, patch-worksite, ADMINISTRATOR, 200",
            "PATCH, /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, patch-worksite, WAREHOUSE_MANAGER, 403",
            "PATCH, /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, patch-worksite, SITE_MANAGER, 403",
            "POST , /api/v1/warehouses, warehouse, ANONYMOUS, 401",
            "POST , /api/v1/warehouses, warehouse, ADMINISTRATOR, 201",
            "POST , /api/v1/warehouses, warehouse, WAREHOUSE_MANAGER, 403",
            "POST , /api/v1/warehouses, warehouse, SITE_MANAGER, 403",
            "GET  , /api/v1/warehouses, , ANONYMOUS, 401",
            "GET  , /api/v1/warehouses, , ADMINISTRATOR, 200",
            "GET  , /api/v1/warehouses, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/warehouses, , SITE_MANAGER, 200",
            "GET  , /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "PATCH, /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, deactivate, ANONYMOUS, 401",
            "PATCH, /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, deactivate, ADMINISTRATOR, 200",
            "PATCH, /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, deactivate, WAREHOUSE_MANAGER, 403",
            "PATCH, /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, deactivate, SITE_MANAGER, 403",
            "POST , /api/v1/materials, material, ANONYMOUS, 401",
            "GET  , /api/v1/materials, , ANONYMOUS, 401",
            "GET  , /api/v1/materials/00000000-0000-0000-0000-0000000000aa, , ANONYMOUS, 401",
            "PATCH, /api/v1/materials/00000000-0000-0000-0000-0000000000aa, patch-material, ANONYMOUS, 401",
            "POST , /api/v1/materials, material, ADMINISTRATOR, 201",
            "GET  , /api/v1/materials, , ADMINISTRATOR, 200",
            "GET  , /api/v1/materials/00000000-0000-0000-0000-0000000000aa, , ADMINISTRATOR, 200",
            "PATCH, /api/v1/materials/00000000-0000-0000-0000-0000000000aa, patch-material, ADMINISTRATOR, 200",
            "POST , /api/v1/materials, material, WAREHOUSE_MANAGER, 403",
            "GET  , /api/v1/materials, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/materials/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "PATCH, /api/v1/materials/00000000-0000-0000-0000-0000000000aa, patch-material, WAREHOUSE_MANAGER, 403",
            "POST , /api/v1/materials, material, SITE_MANAGER, 403",
            "GET  , /api/v1/materials, , SITE_MANAGER, 200",
            "GET  , /api/v1/materials/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "PATCH, /api/v1/materials/00000000-0000-0000-0000-0000000000aa, patch-material, SITE_MANAGER, 403",
            "POST , /api/v1/assignments, assignment, ANONYMOUS, 401",
            "GET  , /api/v1/assignments, , ANONYMOUS, 401",
            "GET  , /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, , ANONYMOUS, 401",
            "PATCH, /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, end-assignment, ANONYMOUS, 401",
            "POST , /api/v1/assignments, assignment, ADMINISTRATOR, 201",
            "GET  , /api/v1/assignments, , ADMINISTRATOR, 200",
            "GET  , /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, , ADMINISTRATOR, 200",
            "PATCH, /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, end-assignment, ADMINISTRATOR, 200",
            "POST , /api/v1/assignments, assignment, WAREHOUSE_MANAGER, 403",
            "GET  , /api/v1/assignments, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "PATCH, /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, end-assignment, WAREHOUSE_MANAGER, 403",
            "POST , /api/v1/assignments, assignment, SITE_MANAGER, 403",
            "GET  , /api/v1/assignments, , SITE_MANAGER, 200",
            "GET  , /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "PATCH, /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, end-assignment, SITE_MANAGER, 403",
    })
    void endpoint_is_allowed_only_for_its_roles(String method, String path, String body, String who, int expectedStatus)
            throws Exception {
        MockHttpServletRequestBuilder request = switch (method) {
            case "GET" -> get(path);
            case "PATCH" -> patch(path);
            default -> post(path);
        };
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(BODIES.get(body));
        }
        if (!"ANONYMOUS".equals(who)) {
            String token = issuer.issue(new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), who)).value();
            request.header("Authorization", "Bearer " + token);
        }

        mvc.perform(request).andExpect(status().is(expectedStatus));
    }

    @Test
    void every_endpoint_declares_its_authorization_rule() {
        List<String> withoutRule = handlerMapping.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getPackageName().startsWith("pe.buildshield.organization"))
                .filter(handler -> !hasPreAuthorize(handler))
                .map(handler -> handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName())
                .toList();

        assertThat(handlerMapping.getHandlerMethods()).isNotEmpty();
        assertThat(withoutRule).as("endpoints sin @PreAuthorize").isEmpty();
    }

    private static boolean hasPreAuthorize(HandlerMethod handler) {
        return handler.hasMethodAnnotation(PreAuthorize.class)
                || handler.getBeanType().isAnnotationPresent(PreAuthorize.class);
    }
}
