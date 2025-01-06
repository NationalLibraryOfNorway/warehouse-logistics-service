package no.nb.mlt.wls.infrastructure

import no.nb.mlt.wls.domain.WLSService
import no.nb.mlt.wls.infrastructure.callbacks.InventoryNotifierAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.ItemRepositoryMongoAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.synq.SynqAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

@Configuration
class BeansConfig {
    @Bean
    fun addNewItem(
        synqAdapter: SynqAdapter,
        itemMongoAdapter: ItemRepositoryMongoAdapter,
        orderMongoAdapter: MongoOrderRepositoryAdapter,
        callbackHandler: InventoryNotifierAdapter,
        mailSender: JavaMailSender
    ) = WLSService(itemMongoAdapter, orderMongoAdapter, synqAdapter, callbackHandler, mailSender)
}
