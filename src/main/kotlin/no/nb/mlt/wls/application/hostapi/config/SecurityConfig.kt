package no.nb.mlt.wls.application.hostapi.config

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

@Configuration
@Profile("!pipeline")
@EnableWebFluxSecurity
class SecurityConfig {
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    val issuerUri: String = ""

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun hostSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http {
            csrf { }
            authorizeExchange {
                authorize("/api-docs", permitAll)
                authorize("/api-docs/**", permitAll)
                authorize("/swagger", permitAll)
                authorize("/swagger/**", permitAll)
                authorize("/webjars/swagger-ui/**", permitAll)
                authorize("/actuator", permitAll)
                authorize("/actuator/**", permitAll)
                authorize("/v1/item", hasAuthority("SCOPE_wls-item"))
                authorize("/v1/order", hasAuthority("SCOPE_wls-order"))
                authorize(anyExchange, authenticated)
            }
            oauth2ResourceServer {
                jwt { }
            }
        }
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun jwtDecoder(): JwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri)
}
