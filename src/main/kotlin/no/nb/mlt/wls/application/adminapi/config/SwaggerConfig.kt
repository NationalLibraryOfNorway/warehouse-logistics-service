package no.nb.mlt.wls.application.adminapi.config

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
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.core.annotation.Order

@Configuration("adminSwaggerConfig")
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
class AdminSwaggerConfig {
    @Bean("adminApi")
    fun synqApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("Admin API")
            .displayName("Admin API")
            .pathsToMatch("/hermes-admin/**")
            .build()
}
