package no.nb.mlt.wls.core.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.OAuthFlow
import io.swagger.v3.oas.annotations.security.OAuthFlows
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.OAUTH2,
    flows = OAuthFlows(
        clientCredentials = OAuthFlow(
            tokenUrl = "\${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/token",
            scopes = []
        )
    )
)
class SwaggerConfig {
    @Bean
    fun openApi(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Hermes WLS (Warehouse and Logistics Service) middleware")
                    .description(
                        """
                        Hermes is developed by the MLT (Warehouse and Logistics team) at the National Library of Norway (NLN).
                        Hermes facilitates communication between the NLN's storage systems and the cataloging systems.
                        Hermes' name is inspired by the Greek deity Hermes, who was the herald of the gods.

                        Applications that need to use Hermes must authenticate with a JWT token.
                        They can get it from the NLN's instance of Keycloak with help of a Service Account client.
                        If you are unsure how to do this, please contact the MLT team for help.
                        """.trimIndent()
                    ).contact(
                        Contact()
                            .name("MLT team at the National Library of Norway")
                            .email("mlt@nb.no")
                            .url("https://www.nb.no")
                    )
            )
            .addSecurityItem(
                SecurityRequirement().addList("bearerAuth")
            )
    }
}
