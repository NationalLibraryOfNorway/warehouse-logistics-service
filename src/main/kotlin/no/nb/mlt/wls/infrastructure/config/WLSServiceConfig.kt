package no.nb.mlt.wls.infrastructure.config

import no.nb.mlt.wls.domain.WLSService
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.OutboxMessageProcessor
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import no.nb.mlt.wls.infrastructure.callbacks.InventoryNotifierAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.ItemRepositoryMongoAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.outbox.MongoOutboxRepositoryAdapter
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
        orderCreatedOutbox: MongoOutboxRepositoryAdapter,
        emailAdapter: EmailNotifier,
        transactionPort: TransactionPort,
        outboxMessageProcessor: OutboxMessageProcessor
    ) = WLSService(
        itemMongoAdapter,
        orderMongoAdapter,
        callbackHandler,
        orderCreatedOutbox,
        transactionPort,
        outboxMessageProcessor
    )
}
