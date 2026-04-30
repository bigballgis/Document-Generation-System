package com.docgen.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 configuration for automatic API documentation generation.
 * Exposes Swagger UI at {@code /swagger-ui.html} and API docs at {@code /api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI docgenOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Document Generation System API")
                        .description("Low-Code Document Generation System RESTful API. "
                                + "Supports template management, document generation, "
                                + "async/batch processing, and more.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("DocGen Team")
                                .email("support@docgen.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addTagsItem(new Tag().name("Parameter").description("Parameter definitions"))
                .addSecurityItem(new SecurityRequirement()
                        .addList("Bearer Authentication")
                        .addList("API Key"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Bearer token authentication"))
                        .addSecuritySchemes("API Key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-API-Key")
                                        .description("API Key authentication")));
    }
}
