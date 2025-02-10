package no.nb.mlt.wls.infrastructure.email

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import no.nb.mlt.wls.domain.model.HostEmail
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EmailRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer
import java.awt.image.BufferedImage

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
        logger.info {
            "Sending emails for order ${order.hostOrderId}"
        }
        sendEmail(
            createOrderEmail(order, receiver),
            "Email sent to host"
        ) { error ->
            "Failed to send order confirmation emails: ${error.message}"
        }
        sendEmail(
            createStorageOrderEmail(order, orderItems),
            "Email sent to order handlers"
        ) { error ->
            "Failed to send orders: ${error.message}"
        }
    }

    override suspend fun orderUpdated(order: Order) {
        TODO("Not yet implemented")
    }

    /**
     * Send an email and log any exceptions
     */
    private fun sendEmail(
        email: MimeMessage?,
        successInfo: String,
        errorHandler: (e: Exception) -> Any
    ) {
        try {
            emailSender.send(email)
            logger.info {
                successInfo
            }
        } catch (e: MailException) {
            errorHandler(e)
            if (logger.isDebugEnabled()) {
                e.printStackTrace()
            }
        }
    }

    private fun createOrderEmail(
        order: Order,
        receiver: HostEmail
    ): MimeMessage {
        val type = "order-confirmation.ftl"
        val mail = emailSender.createMimeMessage()
        val helper = MimeMessageHelper(mail, false)
        val template = freeMarkerConfigurer.configuration.getTemplate(type)
        val htmlBody =
            FreeMarkerTemplateUtils.processTemplateIntoString(
                template,
                mapOf("order" to order, "ordertype" to translateOrderType(order.orderType))
            )

        // Email Metadata
        helper.setText(htmlBody, true)
        helper.setSubject("Bestillingsbekreftelse fra WLS - ${order.hostOrderId}")
        helper.setFrom("noreply@wls-api.no")
        helper.setTo(receiver.email)

        return helper.mimeMessage
    }

    // TODO - Is this relevant for the domain, or should translations be handled explicitly somewhere else?
    private fun translateOrderType(orderType: Order.Type): String {
        return when (orderType) {
            Order.Type.LOAN -> "Lån"
            Order.Type.DIGITIZATION -> "Digitalisering"
        }
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
        val type = "order.ftl"
        val template = freeMarkerConfigurer.configuration.getTemplate(type)
        val emailOrderItems = computeItemsWithQrCodes(orderItems)
        val htmlBody =
            FreeMarkerTemplateUtils.processTemplateIntoString(
                template,
                mapOf(
                    "order" to order,
                    "orderQrCode" to getQrHtmlString(order.hostOrderId),
                    "orderItems" to emailOrderItems
                )
            )
        helper.setText(htmlBody, true)
        helper.setSubject("Ny bestilling fra ${order.hostName} - ${order.hostOrderId}")
        helper.setFrom("noreply@wls-api.no")
        helper.setTo(storageEmail)
        // QR-Code image handling for order ID and order items
        val orderIdQrImage = BarcodeUtils.createQrImage(order.hostOrderId, scale = 3, border = 4)
        helper.rootMimeMultipart.addBodyPart(createImagePart(orderIdQrImage, order.hostOrderId))
        for (orderItem in emailOrderItems) {
            helper.rootMimeMultipart.addBodyPart(createImagePart(orderItem.image, orderItem.item.hostId))
        }
        return helper.mimeMessage
    }

    private fun createImagePart(
        image: BufferedImage,
        cid: String
    ): MimeBodyPart {
        val imagePart = MimeBodyPart()
        imagePart.disposition = MimeBodyPart.INLINE
        imagePart.contentID = "qr-$cid"
        imagePart.dataHandler = BarcodeUtils.createImageDataHandler(image)
        return imagePart
    }

    private fun computeItemsWithQrCodes(items: List<Item>): List<EmailOrderItem> {
        val list = mutableListOf<EmailOrderItem>()
        for (item in items) {
            list.add(EmailOrderItem(item, getQrHtmlString(item.hostId), BarcodeUtils.createQrImage(item.hostId)))
        }
        return list
    }

    private fun getQrHtmlString(cid: String): String =
        """
            <img src="cid:qr-$cid" alt="qrcode"/></img>
        """

    data class EmailOrderItem(val item: Item, val qr: String, val image: BufferedImage)
}
