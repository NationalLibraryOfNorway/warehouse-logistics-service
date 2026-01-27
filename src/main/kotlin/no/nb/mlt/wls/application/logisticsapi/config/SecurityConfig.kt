package no.nb.mlt.wls.application.logisticsapi.config

import no.nb.mlt.wls.application.hostapi.config.SecurityConfig.RealmAccessToAuthoritiesConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher

@Configuration("logisticsSecurityConfig")
@Profile("!pipeline")
class SecurityConfig {
    @Value($$"${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    val issuerUri: String = ""

    @Bean("logisticsSecurityFilterChain")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun logisticsSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http {
            securityMatcher(PathPatternParserServerWebExchangeMatcher("/hermes/logistics/**"))
            authorizeExchange {
                authorize("/hermes/logistics/**", hasRole("logistics"))
                authorize(anyExchange, authenticated)
            }
            oauth2ResourceServer {
                jwt { jwtAuthenticationConverter = RealmAccessToAuthoritiesConverter() }
            }
        }

    @Bean("logisticsJwtDecoder")
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun jwtDecoder(): JwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri)
}
