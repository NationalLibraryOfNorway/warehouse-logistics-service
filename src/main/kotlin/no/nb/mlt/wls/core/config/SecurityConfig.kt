package no.nb.mlt.wls.core.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
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
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
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
                authorize("/v1/product", hasAuthority("SCOPE_wls-product"))
                authorize("/product", hasAuthority("SCOPE_wls-product"))
                authorize("/v1/order", hasAuthority("SCOPE_wls-order"))
                authorize("/order", hasAuthority("SCOPE_wls-order"))
                authorize(anyExchange, authenticated)
            }
            oauth2ResourceServer {
                jwt { }
            }
        }
    }

    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri)
}
