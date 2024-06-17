package no.nb.mlt.wls.core.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono

@Configuration
@Profile("!local-dev")
@EnableWebFluxSecurity
class SecurityConfig {
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    val issuerUri: String = ""

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
                    jwt.jwtDecoder(jwtDecoder())
                }
            }
        return http.build()
    }

    @Bean
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

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder {
        val jwtDecoder = ReactiveJwtDecoders.fromIssuerLocation(issuerUri) as NimbusReactiveJwtDecoder
        val audienceValidator = AudienceValidator()
        val withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri)
        val withAudience = DelegatingOAuth2TokenValidator(withIssuer, audienceValidator)
        jwtDecoder.setJwtValidator(withAudience)

        return jwtDecoder
    }

    internal class AudienceValidator : OAuth2TokenValidator<Jwt> {
        val error: OAuth2Error = OAuth2Error("invalid_token", "The required audience is missing", null)

        override fun validate(jwt: Jwt): OAuth2TokenValidatorResult {
            return if (jwt.audience.contains("wls")) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(error)
            }
        }
    }
}
