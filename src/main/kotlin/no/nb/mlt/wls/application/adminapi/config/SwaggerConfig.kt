package no.nb.mlt.wls.application.adminapi.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.OAuthFlow
import io.swagger.v3.oas.annotations.security.OAuthFlows
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration("adminSwaggerConfig")
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
class SwaggerConfig(
    @param:Value($$"${springdoc.server-url}") private val serverUrl: String
) {
    @Bean("adminApi")
    fun adminApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("Admin API")
            .displayName("Admin API")
            .addOpenApiCustomizer {
                it.info.title = "Kardex updates port for Hermes WLS"
                it.info.description =
                    """
                    Hermes, developed and maintained by the Warehouse and Logistics team (MLT) at the National Library of Norway (NLN).
                    Hermes facilitates communication between the NLN's storage systems and catalogues, serving as a master system.

                    This submodule of Hermes provides system administrators an easy way to trigger admin specific jobs when needed.
                    """.trimIndent()
                it.info.contact =
                    Contact()
                        .name("MLT team at the National Library of Norway")
                        .email("mlt@nb.no")
                        .url("https://www.nb.no")
                it.security = listOf((SecurityRequirement().addList("clientCredentials")))
                it.servers = listOf(Server().url(serverUrl))
            }.pathsToMatch("/hermes/admin/**")
            .build()
}
