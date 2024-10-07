package no.nb.mlt.wls.application.synqapi.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.OAuthFlow
import io.swagger.v3.oas.annotations.security.OAuthFlows
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration("SynqSwaggerConfig")
@SecurityScheme(
    name = "clientCredentials",
    type = SecuritySchemeType.OAUTH2,
    flows =
        OAuthFlows(
            clientCredentials =
                OAuthFlow(
                    tokenUrl = "\${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/token",
                    scopes = []
                )
        )
)
class SwaggerConfig {
    @Bean("synqOpenApi")
    fun openApi(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("SynQ updates port for Hermes WLS")
                    .description(
                        """
                        Hermes is developed by the MLT (Warehouse and Logistics team) at the National Library of Norway (NLN).
                        Hermes facilitates communication between the NLN's storage systems and the cataloging systems.
                        This submodule of Hermes is responsible for receiving product/item and order updates from SynQ.
                        Hermes will then use these to convert the updates into a format that can be used by the rest of the system.
                        And send these updates along to appropriate catalogs and update internal item and order information.
                        """.trimIndent()
                    ).contact(
                        Contact()
                            .name("MLT team at the National Library of Norway")
                            .email("mlt@nb.no")
                            .url("https://www.nb.no")
                    )
            )
            .addSecurityItem(
                SecurityRequirement().addList("clientCredentials")
            )
    }

    @Bean("synqApi")
    fun synqApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("SynQ API")
            .displayName("SynQ updates port for Hermes WLS")
            .pathsToMatch("/synq/v1/**")
            .build()
    }
}
