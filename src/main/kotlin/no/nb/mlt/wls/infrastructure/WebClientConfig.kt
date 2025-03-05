package no.nb.mlt.wls.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.transport.ProxyProvider

@Configuration
class WebClientConfig {
    @Bean("nonProxyWebClient")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun nonProxyWebClient(builder: WebClient.Builder): WebClient = builder.build()

    @Bean("proxyWebClient")
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun proxyWebClient(
        builder: WebClient.Builder,
        proxyConfig: ProxyConfig
    ): WebClient {
        val httpClient =
            HttpClient.create().proxy {
                it.type(ProxyProvider.Proxy.HTTP)
                    .host(proxyConfig.httpProxyHost)
                    .port(proxyConfig.httpProxyPort)
                    .nonProxyHosts(proxyConfig.nonProxyHosts)
            }

        return builder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
