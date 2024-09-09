package no.nb.mlt.wls.infrastructure.config

import no.nb.mlt.wls.domain.WLSService
import no.nb.mlt.wls.infrastructure.repository.ItemRepositoryMongoAdapter
import no.nb.mlt.wls.infrastructure.synq.SynqAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class BeansConfig {
    @Bean
    fun addNewItem(
        synqAdapter: SynqAdapter,
        webClient: WebClient,
        itemMongoAdapter: ItemRepositoryMongoAdapter
    ) = WLSService(itemMongoAdapter, synqAdapter)
}
