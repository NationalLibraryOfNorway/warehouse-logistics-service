package no.nb.mlt.wls.infrastructure.email

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.Message
import jakarta.mail.internet.MimeMessage
import no.nb.mlt.wls.domain.model.HostEmail
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EmailRepository
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender

private val logger = KotlinLogging.logger {}

class EmailAdapter(
    private val emailRepository: EmailRepository,
    private val emailSender: JavaMailSender
) : EmailNotifier {
    override suspend fun orderCreated(order: Order) {
        val receiver = emailRepository.getHostEmail(order.hostName) ?: return
        val email = createOrderEmail(order, receiver)
        if (email != null) {
            try {
                emailSender.send(email)
                logger.info {
                    "Email sent to ${email.allRecipients.first()}"
                }
            } catch (e: MailException) {
                // TODO - Review. Should probably be explicitly logged?
                e.printStackTrace()
            }
        }
    }

    override suspend fun orderUpdated(order: Order) {
        TODO("Not yet implemented")
    }

    private fun createOrderEmail(
        order: Order,
        receiver: HostEmail
    ): MimeMessage? {
        val mail =
            when (order.orderType) {
                Order.Type.LOAN -> EmailStructures.createLoanMessage(order, emailSender)
                Order.Type.DIGITIZATION -> {
                    logger.error {
                        "WLS does not support sending emails for digitization"
                    }
                    return null
                }
            }
        mail.setRecipients(Message.RecipientType.TO, receiver.email)
        return mail
    }
}
