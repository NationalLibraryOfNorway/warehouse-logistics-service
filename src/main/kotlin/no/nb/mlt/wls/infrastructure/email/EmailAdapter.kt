package no.nb.mlt.wls.infrastructure.email

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.internet.MimeMessage
import jakarta.mail.util.ByteArrayDataSource
import no.nb.mlt.wls.domain.model.HostEmail
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EmailRepository
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer
import org.xhtmlrenderer.pdf.ITextRenderer
import java.io.ByteArrayOutputStream

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
        val receiver = emailRepository.getHostEmail(order.hostName) ?: return
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
        val helper = MimeMessageHelper(mail, false)
        val template = freeMarkerConfigurer.configuration.getTemplate(type)
        val htmlBody = FreeMarkerTemplateUtils.processTemplateIntoString(template, mapOf("order" to order))

        // Email Metadata
        helper.setText(htmlBody, true)
        helper.setSubject("Bestillingsbekreftelse fra WLS - ${order.hostOrderId}")
        helper.setFrom("noreply@nb.no")
        helper.setTo(receiver.email)

        return helper.mimeMessage
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
        val helper = MimeMessageHelper(mail, true)
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
        helper.setText(htmlBody, true)
        helper.setSubject("Ny bestilling fra ${order.hostName} - ${order.hostOrderId}")
        helper.setFrom("noreply@nb.no")
        helper.setTo(storageEmail)
        attachPdf(helper, order, htmlBody)
        return helper.mimeMessage
    }

    /**
     * Parses a HTML-string into XHTML, converts it to PDF, and attaches it to the provided
     * MimeMessageHelper
     */
    private fun attachPdf(
        helper: MimeMessageHelper,
        order: Order,
        html: String
    ) {
        val xhtml = Jsoup.parse(html).html()
        val renderer = ITextRenderer()
        val sharedCtx = renderer.sharedContext
        sharedCtx.isPrint = true
        sharedCtx.isInteractive = false
        renderer.setDocumentFromString(xhtml)
        renderer.layout()
        val outputStream = ByteArrayOutputStream()
        renderer.createPDF(outputStream)
        helper.addAttachment(
            "wls-order-confirmation_${order.hostOrderId}.pdf",
            ByteArrayDataSource(outputStream.toByteArray(), APPLICATION_PDF_VALUE)
        )
    }
}
