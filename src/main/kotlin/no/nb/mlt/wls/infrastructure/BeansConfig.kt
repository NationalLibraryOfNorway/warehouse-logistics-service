package no.nb.mlt.wls.infrastructure

import no.nb.mlt.wls.domain.WLSService
import no.nb.mlt.wls.infrastructure.repositories.item.ItemRepositoryMongoAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.synq.SynqAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BeansConfig {
    @Bean
    fun addNewItem(
        synqAdapter: SynqAdapter,
        itemMongoAdapter: ItemRepositoryMongoAdapter,
        orderMongoAdapter: MongoOrderRepositoryAdapter
    ) = WLSService(itemMongoAdapter, orderMongoAdapter, synqAdapter)
}
