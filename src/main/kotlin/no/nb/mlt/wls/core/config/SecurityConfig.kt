package no.nb.mlt.wls.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono

@Configuration
class SecurityConfig {
    @Configuration
    @EnableWebFluxSecurity
    class SecurityEnabled {
        @Bean
        fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
            http
                .csrf { csrf -> csrf.disable() }
                .authorizeExchange { exchange ->
                    exchange
                        .pathMatchers(
                            "/api-docs",
                            "/api-docs/**",
                            "/swagger",
                            "/swagger/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/swagger-resources",
                            "/swagger-resources/**",
                            "/webjars/swagger-ui/**",
                            "/actuator",
                            "/actuator/**"
                        ).permitAll()
                        .pathMatchers(HttpMethod.GET, "/v1/test/authorized").hasAuthority("wls-dev-role")
                        .pathMatchers(HttpMethod.GET, "/v1/test/authenticated").authenticated()
                        .pathMatchers(HttpMethod.GET, "/v1/test/open").permitAll()
                }
                .oauth2ResourceServer { server ->
                    server.jwt { jwt ->
                        jwt.jwtAuthenticationConverter(authoritiesExtractor())
                    }
                }
            return http.build()
        }

        fun authoritiesExtractor(): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
            val jwtAuthConverter = JwtAuthenticationConverter()
            jwtAuthConverter.setJwtGrantedAuthoritiesConverter(GrantedAuthoritiesExtractor())
            jwtAuthConverter.setPrincipalClaimName("preferred_username")
            return ReactiveJwtAuthenticationConverterAdapter(jwtAuthConverter)
        }

        internal class GrantedAuthoritiesExtractor : Converter<Jwt, Collection<GrantedAuthority>> {
            override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
                @Suppress("UNCHECKED_CAST")
                val realmAccess = jwt.claims.getOrDefault("realm_access", emptyMap<String, List<String>>()) as Map<String, List<String>>
                val roles = realmAccess.getOrDefault("roles", emptyList())
                return roles.map { SimpleGrantedAuthority(it) }
            }
        }
    }
}
