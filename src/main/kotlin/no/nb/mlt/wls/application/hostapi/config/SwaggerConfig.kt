package no.nb.mlt.wls.application.hostapi.config

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
import org.springframework.context.annotation.Primary

@Configuration
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
    @Bean
    @Primary
    fun openApi(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Hermes WLS (Warehouse and Logistics Service) middleware")
                    .description(
                        """
                        Hermes, developed and maintained by the Warehouse and Logistics team (MLT) at the National Library of Norway (NLN).
                        Hermes facilitates communication between the NLN's storage systems and catalogues, serving as a master system.
                        Hermes' name is inspired by the Greek deity Hermes, who was known as the herald of the gods.

                        This submodule of Hermes is responsible for receiving item masters and order requests from catalogues.
                        These are then sent along to storage systems, so that items can be stored or retrieved, and orders can be processed.

                        Applications that want to use Hermes must authenticate with a JWT token.
                        It is provided by the NLN's instance of Keycloak with help of a Service Account client.
                        Please contact the MLT team for help getting set up with one or to reset credentials.
                        """.trimIndent()
                    ).contact(
                        Contact()
                            .name("MLT team at the National Library of Norway")
                            .email("mlt@nb.no")
                            .url("https://www.nb.no")
                    ).version("1.0.0")
            )
            .addSecurityItem(
                SecurityRequirement().addList("clientCredentials")
            )
    }

    @Bean
    fun hostApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("Host API")
            .displayName("API for catalogues (hosts) to interact with Hermes WLS")
            .pathsToMatch("/v1/**")
            .build()
    }
}
