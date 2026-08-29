package com.sogeco.fleet.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI sogecoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SOGECO Fleet Manager API")
                        .version("1.0.0")
                        .description("""
                                API de la plateforme de gestion de flotte SOGECO Sarl.

                                Toutes les listes sont paginees et renvoyees dans une enveloppe PageResponse.
                                Les erreurs suivent le format ProblemDetail (RFC 7807).
                                Les montants sont exprimes en FCFA (XAF).
                                """)
                        .contact(new Contact().name("SOGECO Sarl").email("contact@sogeco.cm")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Developpement")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
