package com.company.procurement.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI procurementOpenApi() {
        return new OpenAPI()
            .components(new Components().addSecuritySchemes(
                BEARER_AUTH,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("仅粘贴当前测试会话的短期 JWT，不要使用生产凭据")
            ))
            .info(new Info()
                .title("Data Center Third-party Operations Management System API")
                .version("v1.0")
                .description("数据中心第三方运营综合管理系统 API 文档"));
    }

    @Bean
    public GlobalOpenApiCustomizer bearerSecurityCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (!isPublicApi(path)) {
                    pathItem.readOperations().forEach(operation ->
                        operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                    );
                }
            });
        };
    }

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
            .group("auth")
            .pathsToMatch("/api/auth/**")
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/admin/**")
            .build();
    }

    @Bean
    public GroupedOpenApi supplierApi() {
        return GroupedOpenApi.builder()
            .group("supplier")
            .pathsToMatch("/api/supplier/**")
            .build();
    }

    private boolean isPublicApi(String path) {
        return "/api/auth/login".equals(path)
            || path.startsWith("/api/auth/feishu/")
            || path.startsWith("/api/public/ehs/")
            || path.startsWith("/api/procurement/ai/images/")
            || path.startsWith("/api/integrations/feishu-bot/")
            || path.startsWith("/api/integrations/shopping/");
    }
}
