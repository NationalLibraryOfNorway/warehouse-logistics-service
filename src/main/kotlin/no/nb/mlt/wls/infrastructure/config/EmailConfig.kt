package no.nb.mlt.wls.infrastructure.config

import no.nb.mlt.wls.infrastructure.email.DisabledEmailAdapter
import no.nb.mlt.wls.infrastructure.email.EmailAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerViewResolver

@Configuration
class EmailConfig {
    @ConditionalOnProperty("spring.mail.host")
    @Bean
    fun emailAdapter(
        emailSender: JavaMailSender,
        freeMarkerConfigurer: FreeMarkerConfigurer
    ) = EmailAdapter(emailSender, freeMarkerConfigurer)

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
}
