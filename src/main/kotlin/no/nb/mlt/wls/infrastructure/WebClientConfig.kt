package no.nb.mlt.wls.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
class WebClientConfig {
    @Bean
    fun webClient(builder: WebClient.Builder): WebClient {
        val httpClient = HttpClient.create().proxyWithSystemProperties()

        return builder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
