package no.nb.mlt.wls.core.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers
import reactor.core.publisher.Mono

@Configuration
class SecurityConfig {

  // Only enable this security configuration if the property security.enabled is set to true
  @ConditionalOnProperty(
    prefix = "security",
    name = ["enabled"],
    havingValue = "true"
  )
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
              "/",
              "/webjars/swagger-ui/**",
              "/v1/api-docs",
              "/v1/api-docs/**",
              "/swagger-ui/**",
              "/swagger-ui.html",
              "/swagger-resources",
              "/swagger-resources/**",
              "/actuator",
              "/actuator/**"
            ).permitAll()
            .pathMatchers(HttpMethod.GET, "/hermes/test/authorized").hasAuthority("wls-dev-role")
            .pathMatchers(HttpMethod.GET, "/hermes/test/authenticated").authenticated()
            .pathMatchers(HttpMethod.GET, "/hermes/test/open").permitAll()
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

  @ConditionalOnProperty(
    prefix = "security",
    name = ["enabled"],
    havingValue = "false"
  )
  @Configuration
  @EnableWebFluxSecurity
  class SecurityDisabled {
    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
      return http
        .csrf { csrf -> csrf.disable() }
        .authorizeExchange { authorizeExchange ->
          authorizeExchange
            .anyExchange()
            .permitAll()
        }.build()
    }
  }
}
