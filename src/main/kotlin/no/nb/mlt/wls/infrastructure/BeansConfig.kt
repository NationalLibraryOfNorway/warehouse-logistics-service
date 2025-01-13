package no.nb.mlt.wls.infrastructure

import no.nb.mlt.wls.domain.WLSService
import no.nb.mlt.wls.infrastructure.callbacks.InventoryNotifierAdapter
import no.nb.mlt.wls.infrastructure.email.EmailAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.ItemRepositoryMongoAdapter
import no.nb.mlt.wls.infrastructure.repositories.mail.MongoEmailRepositoryAdapter
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
        emailAdapter: EmailAdapter
    ) = WLSService(itemMongoAdapter, orderMongoAdapter, synqAdapter, callbackHandler, emailAdapter)

    @Bean
    fun emailAdapter(
        emailRepository: MongoEmailRepositoryAdapter,
        emailSender: JavaMailSender
    ) = EmailAdapter(emailRepository, emailSender)
}
