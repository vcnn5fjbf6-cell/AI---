package com.company.procurement.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void exposesBearerJwtSchemeAndMarksOnlyProtectedOperations() {
        OpenAPI openApi = config.procurementOpenApi()
            .paths(new Paths()
                .addPathItem("/api/auth/login", new PathItem().post(new Operation()))
                .addPathItem("/api/auth/me", new PathItem().get(new Operation()))
                .addPathItem("/api/public/ehs/permit", new PathItem().post(new Operation()))
                .addPathItem("/api/procurement/ai/images/example", new PathItem().get(new Operation())));

        config.bearerSecurityCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openApi.getPaths().get("/api/auth/login").getPost().getSecurity()).isNullOrEmpty();
        assertThat(openApi.getPaths().get("/api/public/ehs/permit").getPost().getSecurity()).isNullOrEmpty();
        assertThat(openApi.getPaths().get("/api/procurement/ai/images/example").getGet().getSecurity())
            .isNullOrEmpty();
        assertThat(openApi.getPaths().get("/api/auth/me").getGet().getSecurity())
            .singleElement()
            .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
    }
}
