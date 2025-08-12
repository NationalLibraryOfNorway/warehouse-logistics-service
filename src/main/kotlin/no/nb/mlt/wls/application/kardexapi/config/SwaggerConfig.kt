package no.nb.mlt.wls.application.kardexapi.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.OAuthFlow
import io.swagger.v3.oas.annotations.security.OAuthFlows
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration("KarexSwaggerConfig")
@SecurityScheme(
    name = "clientCredentials",
    type = SecuritySchemeType.OAUTH2,
    flows =
        OAuthFlows(
            clientCredentials =
                OAuthFlow(
                    tokenUrl = $$"${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/token",
                    scopes = []
                )
        )
)
class SwaggerConfig {
    @Bean("kardexOpenApi")
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Kardex updates port for Hermes WLS")
                    .description(
                        """
                        Hermes, developed and maintained by the Warehouse and Logistics team (MLT) at the National Library of Norway (NLN).
                        Hermes facilitates communication between the NLN's storage systems and catalogues, serving as a master system.

                        This submodule of Hermes is responsible for receiving product/item and order updates from Kardex.
                        Hermes converts these updates into a format that can be used by the rest of the system.
                        These are then sent along to appropriate catalogues, they also update internal item and order information.
                        """.trimIndent()
                    ).contact(
                        Contact()
                            .name("MLT team at the National Library of Norway")
                            .email("mlt@nb.no")
                            .url("https://www.nb.no")
                    )
            ).addSecurityItem(
                SecurityRequirement().addList("clientCredentials")
            )

    @Bean("kardexApi")
    fun kardexApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("Kardex API")
            .displayName("Kardex updates port for Hermes WLS")
            .pathsToMatch("/hermes/kardex/v1/**")
            .build()
}
