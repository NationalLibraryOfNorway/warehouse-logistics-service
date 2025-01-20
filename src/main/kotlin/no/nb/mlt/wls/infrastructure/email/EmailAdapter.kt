package no.nb.mlt.wls.infrastructure.email

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.Message
import jakarta.mail.internet.MimeMessage
import no.nb.mlt.wls.domain.model.HostEmail
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EmailRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer

private val logger = KotlinLogging.logger {}

class EmailAdapter(
    private val emailRepository: EmailRepository,
    private val emailSender: JavaMailSender,
    private val freeMarkerConfigurer: FreeMarkerConfigurer
) : EmailNotifier {
    @Value("\${mail.storage}")
    val storageEmail: String = ""

    override suspend fun orderCreated(
        order: Order,
        orderItems: List<Item>
    ) {
        // TODO - Return guard here
        val receiver = emailRepository.getHostEmail(order.hostName) ?: HostEmail(order.hostName, "noah.aanonli@nb.no")
        try {
            sendOrderEmail(createOrderEmail(order, receiver))
            sendHostOrderEmail(createStorageOrderEmail(order, orderItems))
            logger.info {
                "Emails sent for order ${order.hostOrderId}"
            }
        } catch (e: MailException) {
            // TODO - Review. Should probably be explicitly logged?
            e.printStackTrace()
        }
    }

    override suspend fun orderUpdated(order: Order) {
        TODO("Not yet implemented")
    }

    private fun sendOrderEmail(message: MimeMessage?) {
        if (message != null) {
            try {
                emailSender.send(message)
                logger.info {
                    "Email sent to host"
                }
            } catch (e: MailException) {
                // TODO - Review. Should probably be explicitly logged?
                e.printStackTrace()
            }
        }
    }

    private fun sendHostOrderEmail(message: MimeMessage?) {
        if (message != null) {
            try {
                emailSender.send(message)
                logger.info {
                    "Email sent to storage"
                }
            } catch (e: MailException) {
                // TODO - Review. Should probably be explicitly logged?
                e.printStackTrace()
            }
        }
    }

    private fun createOrderEmail(
        order: Order,
        receiver: HostEmail
    ): MimeMessage? {
        val type =
            when (order.orderType) {
                Order.Type.LOAN -> "order-confirmation.ftl"
                Order.Type.DIGITIZATION -> {
                    logger.error {
                        "WLS does not support sending emails for digitization"
                    }
                    return null
                }
            }

        val mail = emailSender.createMimeMessage()

        val template = freeMarkerConfigurer.configuration.getTemplate(type)
        val htmlBody = FreeMarkerTemplateUtils.processTemplateIntoString(template, mapOf("order" to order))

        mail.setText(htmlBody, Charsets.UTF_8.name(), "html")
        mail.setSubject("Bestillingsbekreftelse fra WLS - ${order.hostOrderId}")
        mail.setFrom("noreply@nb.no")

        mail.setRecipients(Message.RecipientType.TO, receiver.email)
        return mail
    }

    private fun createStorageOrderEmail(
        order: Order,
        orderItems: List<Item>
    ): MimeMessage? {
        if (storageEmail.isBlank()) {
            logger.error {
                "Emails being sent to storage system is disabled"
            }
            return null
        }
        val mail = emailSender.createMimeMessage()
        val type =
            when (order.orderType) {
                Order.Type.LOAN -> "order.ftl"
                Order.Type.DIGITIZATION -> {
                    logger.error {
                        "WLS does not support sending emails for digitization"
                    }
                    return null
                }
            }
        val template = freeMarkerConfigurer.configuration.getTemplate(type)
        val htmlBody = FreeMarkerTemplateUtils.processTemplateIntoString(template, mapOf("order" to order, "orderItems" to orderItems))
        mail.setText(htmlBody, Charsets.UTF_8.name(), "html")
        mail.setSubject("Ny bestilling fra ${order.hostName} - ${order.hostOrderId}")
        mail.setFrom("noreply@nb.no")
        mail.setRecipients(Message.RecipientType.TO, storageEmail)
        return mail
    }
}
