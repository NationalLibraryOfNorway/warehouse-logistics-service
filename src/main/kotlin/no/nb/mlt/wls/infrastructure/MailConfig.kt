package no.nb.mlt.wls.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSenderImpl

@Configuration
class MailConfig {
    @Value("\${email.outgoingServer}")
    val mailServer: String = ""

    @Bean
    fun mailSender(): JavaMailSenderImpl {
        val mailSender = JavaMailSenderImpl()
        mailSender.host = mailServer
        return mailSender
    }
}
