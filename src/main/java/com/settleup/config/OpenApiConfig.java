package com.settleup.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI settleUpOpenApi() {
        final String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("SettleUp API")
                        .version("1.0.0")
                        .description("""
                                Group expense settlement backed by an append-only balance ledger.

                                Two things are worth knowing before calling this API:

                                1. Money is always exchanged as decimal rupees in JSON, but stored
                                   and computed internally as whole paise.
                                2. Write endpoints accept an optional `Idempotency-Key` header.
                                   Supply one and the call becomes safe to retry."""))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
