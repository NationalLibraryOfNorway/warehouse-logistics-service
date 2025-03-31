package no.nb.mlt.wls.application.synqapi.config

import no.nb.mlt.wls.application.hostapi.config.SecurityConfig.RealmAccessToAuthoritiesConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher

@Configuration("SynqSecurityConfig")
@Profile("!pipeline")
@EnableWebFluxSecurity
class SecurityConfig {
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    val issuerUri: String = ""

    @Bean("synqSecurityFilterChain")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun synqSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http {
            securityMatcher(PathPatternParserServerWebExchangeMatcher("/synq/v1/**"))
            csrf { }
            authorizeExchange {
                authorize("/synq/api-docs", permitAll)
                authorize("/synq/api-docs/**", permitAll)
                authorize("/synq/swagger", permitAll)
                authorize("/synq/swagger/**", permitAll)
                authorize("/synq/webjars/swagger-ui/**", permitAll)
                authorize("/synq/v1/**", hasRole("synq"))
                authorize(anyExchange, authenticated)
            }
            oauth2ResourceServer {
                jwt { jwtAuthenticationConverter = RealmAccessToAuthoritiesConverter() }
            }
        }

    @Bean("synqJwtDecoder")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun jwtDecoder(): JwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri)
}
