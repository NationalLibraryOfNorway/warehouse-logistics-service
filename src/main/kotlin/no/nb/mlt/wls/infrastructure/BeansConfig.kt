package no.nb.mlt.wls.infrastructure

import no.nb.mlt.wls.domain.WLSService
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.OutboxMessageProcessor
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import no.nb.mlt.wls.infrastructure.callbacks.InventoryNotifierAdapter
import no.nb.mlt.wls.infrastructure.email.DisabledEmailAdapter
import no.nb.mlt.wls.infrastructure.email.EmailAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.ItemRepositoryMongoAdapter
import no.nb.mlt.wls.infrastructure.repositories.mail.MongoEmailRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.outbox.MongoOutboxRepositoryAdapter
import no.nb.mlt.wls.infrastructure.synq.SynqAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerViewResolver

// TODO - Rename and split this file into relevant/specific configs?
@Configuration
@EnableReactiveMongoAuditing
class BeansConfig {
    @Bean
    fun addNewItem(
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

    @ConditionalOnProperty("spring.mail.host")
    @Bean
    fun emailAdapter(
        emailRepository: MongoEmailRepositoryAdapter,
        emailSender: JavaMailSender,
        freeMarkerConfigurer: FreeMarkerConfigurer
    ) = EmailAdapter(emailRepository, emailSender, freeMarkerConfigurer)

    @ConditionalOnMissingBean(EmailAdapter::class)
    @Bean
    fun disabledEmailAdapter() = DisabledEmailAdapter()

    @Bean
    fun freemarkerViewResolver(): FreeMarkerViewResolver {
        val resolver = FreeMarkerViewResolver()
        resolver.setSuffix(".ftl")
        return resolver
    }

    @Bean
    fun freemarkerConfig(): FreeMarkerConfigurer {
        val freeMarkerConfigurer = FreeMarkerConfigurer()
        freeMarkerConfigurer.setTemplateLoaderPath("classpath:/templates/email/")
        return freeMarkerConfigurer
    }

    @Bean
    fun transactionManager(dbFactory: ReactiveMongoDatabaseFactory): ReactiveMongoTransactionManager {
        return ReactiveMongoTransactionManager(dbFactory)
    }

    @Bean
    fun transactionalOperator(reactiveTransactionManager: ReactiveTransactionManager): TransactionalOperator {
        return TransactionalOperator.create(reactiveTransactionManager)
    }
}
