package no.nb.mlt.wls.infrastructure

import no.nb.mlt.wls.domain.WLSService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BeansConfig {
    @Bean
    fun addNewItem() = WLSService()
}
