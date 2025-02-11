package no.nb.mlt.wls.infrastructure

import no.nb.mlt.wls.domain.WLSService
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.infrastructure.callbacks.InventoryNotifierAdapter
import no.nb.mlt.wls.infrastructure.email.DisabledEmailAdapter
import no.nb.mlt.wls.infrastructure.email.EmailAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.ItemRepositoryMongoAdapter
import no.nb.mlt.wls.infrastructure.repositories.mail.MongoEmailRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.synq.SynqAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerViewResolver

// TODO - Rename and split this file into relevant/specific configs?
@Configuration
class BeansConfig {
    @Bean
    fun addNewItem(
        synqAdapter: SynqAdapter,
        itemMongoAdapter: ItemRepositoryMongoAdapter,
        orderMongoAdapter: MongoOrderRepositoryAdapter,
        callbackHandler: InventoryNotifierAdapter,
        emailAdapter: EmailNotifier
    ) = WLSService(itemMongoAdapter, orderMongoAdapter, synqAdapter, callbackHandler, emailAdapter)

    @ConditionalOnMissingBean(JavaMailSender::class)
    @Bean
    fun disabledEmailAdapter() = DisabledEmailAdapter()

    @ConditionalOnBean(JavaMailSender::class)
    @Bean
    fun emailAdapter(
        emailRepository: MongoEmailRepositoryAdapter,
        emailSender: JavaMailSender,
        freeMarkerConfigurer: FreeMarkerConfigurer
    ) = EmailAdapter(emailRepository, emailSender, freeMarkerConfigurer)

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
}
