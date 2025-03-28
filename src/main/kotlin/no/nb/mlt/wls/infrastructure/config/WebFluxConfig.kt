package no.nb.mlt.wls.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class WebFluxConfig : WebFluxConfigurer {
    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        // Set max in memory size to 1000KB to handle reconciliation requests.
        configurer.defaultCodecs().maxInMemorySize(1000 * 1024)
    }
}
