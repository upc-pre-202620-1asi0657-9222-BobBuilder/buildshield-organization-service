package pe.buildshield.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pe.buildshield.organization.support.OrganizationIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La especificación OpenAPI es pública y documenta los endpoints de organization con su esquema de seguridad. */
@OrganizationIntegrationTest
@AutoConfigureMockMvc
class OpenApiDocumentationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void api_docs_are_public_and_describe_the_organization_endpoints() throws Exception {
        String body = mvc.perform(get("/api/v1/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode spec = json.readTree(body);

        assertThat(spec.path("paths").fieldNames()).toIterable().contains(
                "/api/v1/organizations/me", "/api/v1/worksites", "/api/v1/warehouses", "/api/v1/materials",
                "/api/v1/assignments");
        assertThat(spec.at("/paths/~1api~1v1~1materials/post/responses").fieldNames()).toIterable()
                .contains("201", "400", "403", "409");
        assertThat(spec.at("/components/securitySchemes/bearer/scheme").asText()).isEqualTo("bearer");
        assertThat(spec.at("/paths/~1api~1v1~1worksites/post/summary").asText()).isEqualTo("Registrar una obra");
    }

    @Test
    void swagger_ui_is_public() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
