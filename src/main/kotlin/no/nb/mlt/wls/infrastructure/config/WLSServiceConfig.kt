package no.nb.mlt.wls.infrastructure.config

import no.nb.mlt.wls.domain.WLSService
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import no.nb.mlt.wls.infrastructure.repositories.event.MongoCatalogEventRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.event.MongoEmailEventRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.event.MongoStorageEventRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.MongoItemRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WLSServiceConfig {
    @Bean
    fun wlsService(
        itemMongoAdapter: MongoItemRepositoryAdapter,
        orderMongoAdapter: MongoOrderRepositoryAdapter,
        catalogEventMongoAdapter: MongoCatalogEventRepositoryAdapter,
        storageEventMongoAdapter: MongoStorageEventRepositoryAdapter,
        emailEventMongoAdapter: MongoEmailEventRepositoryAdapter,
        transactionPort: TransactionPort,
        catalogEventProcessor: EventProcessor<CatalogEvent>,
        storageEventProcessor: EventProcessor<StorageEvent>
    ) = WLSService(
        itemMongoAdapter,
        orderMongoAdapter,
        catalogEventMongoAdapter,
        storageEventMongoAdapter,
        emailEventMongoAdapter,
        transactionPort,
        catalogEventProcessor,
        storageEventProcessor
    )
}
