package no.nb.mlt.wls.application.hostapi.config

import no.nb.mlt.wls.domain.model.HostName
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.util.stream.Collectors

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
                authorize("/v1/item", hasRole("item"))
                authorize("/v1/item/**", hasRole("item"))
                authorize("/v1/order", hasRole("order"))
                authorize("/v1/order/**", hasRole("order"))
                authorize(anyExchange, authenticated)
            }
            oauth2ResourceServer {
                jwt { jwtAuthenticationConverter = RealmAccessToAuthoritiesConverter() }
            }
        }
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun jwtDecoder(): JwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri)

    internal class RealmAccessToAuthoritiesConverter : Converter<Jwt, Mono<AbstractAuthenticationToken>> {
        override fun convert(jwt: Jwt): Mono<AbstractAuthenticationToken> {
            var realmAccess =
                jwt.getClaimAsMap("realm_access") ?: throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "JWT does not contain a \"realm_access\" claim"
                )

            var rolesList =
                realmAccess["roles"] as? List<*> ?: throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "JWT does not contain a \"realm_access\" claim with a valid \"roles\" field"
                )

            var roles = rolesList.stream().map { SimpleGrantedAuthority(it.toString()) }.collect(Collectors.toList())

            return Mono.just(JwtAuthenticationToken(jwt, roles))
        }
    }
}

fun JwtAuthenticationToken.checkIfAuthorized(host: HostName) {
    if (authorities.any { it.authority.contains(host.name.lowercase()) }) return

    throw ResponseStatusException(
        HttpStatus.FORBIDDEN,
        "Client $name does not have a role that gives access to resources owned by $host"
    )
}
