package no.nb.mlt.wls.infrastructure.config

import no.nb.mlt.wls.domain.WLSService
import no.nb.mlt.wls.domain.model.storageEvents.StorageEvent
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import no.nb.mlt.wls.infrastructure.callbacks.InventoryNotifierAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.ItemRepositoryMongoAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.event.MongoStorageEventRepositoryAdapter
import no.nb.mlt.wls.infrastructure.synq.SynqAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WLSServiceConfig {
    @Bean
    fun wlsService(
        synqAdapter: SynqAdapter,
        itemMongoAdapter: ItemRepositoryMongoAdapter,
        orderMongoAdapter: MongoOrderRepositoryAdapter,
        callbackHandler: InventoryNotifierAdapter,
        storageEventAdapter: MongoStorageEventRepositoryAdapter,
        emailAdapter: EmailNotifier,
        transactionPort: TransactionPort,
        storageEventProcessor: EventProcessor<StorageEvent>
    ) = WLSService(
        itemMongoAdapter,
        orderMongoAdapter,
        callbackHandler,
        storageEventAdapter,
        transactionPort,
        storageEventProcessor
    )
}
