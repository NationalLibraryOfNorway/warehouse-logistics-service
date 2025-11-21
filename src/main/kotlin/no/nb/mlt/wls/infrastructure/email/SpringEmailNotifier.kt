package no.nb.mlt.wls.infrastructure.email

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.OrderEmail
import no.nb.mlt.wls.domain.ports.outbound.UserNotifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer
import java.awt.image.BufferedImage

private val logger = KotlinLogging.logger {}

class SpringEmailNotifier(
    private val emailSender: JavaMailSender,
    private val freeMarkerConfigurer: FreeMarkerConfigurer
) : UserNotifier {
    @Value($$"${wls.order.sender.email}")
    val senderEmail: String = ""

    @Value($$"${wls.order.handler.email}")
    val storageEmail: String = ""

    override suspend fun orderConfirmation(order: Order): Boolean =
        sendEmail(
            createOrderConfirmationEmail(order),
            "Email sent to host",
            "Failed to send order confirmation email"
        )

    override suspend fun orderPickup(orderEmail: OrderEmail): Boolean =
        sendEmail(
            createOrderPickupMail(orderEmail),
            "Sent order pickup email to order handlers",
            "Failed to send pickup order email"
        )

    override suspend fun orderCompleted(order: Order): Boolean {
        logger.warn { "not yet implemented" }
        return true
    }

    /**
     * Send an email and log any exceptions
     */
    private fun sendEmail(
        email: MimeMessage?,
        successInfo: String,
        errorInfo: String
    ): Boolean {
        if (email == null) {
            logger.error {
                errorInfo
            }
            logger.error {
                "Cannot send email because message is null"
            }
            return false
        }
        try {
            emailSender.send(email)
            logger.info {
                successInfo
            }
            return true
        } catch (e: MailException) {
            logger.error {
                errorInfo + ": ${e.message}"
            }
            if (logger.isDebugEnabled()) {
                e.printStackTrace()
            }
            return false
        } catch (e: Exception) {
            logger.error(e) {
                "Unexpected exception while sending email: ${e.message}"
            }
            return false
        }
    }

    private fun createOrderConfirmationEmail(order: Order): MimeMessage? {
        val receiver = order.contactEmail
        if (receiver.isNullOrBlank()) {
            logger.warn {
                "Contact email is not present for order ${order.hostOrderId}, so the order confirmation email can't be sent"
            }
            return null
        }
        val type = "order-confirmation.ftl"
        val mail = emailSender.createMimeMessage()
        val helper = MimeMessageHelper(mail, false)
        val template = freeMarkerConfigurer.configuration.getTemplate(type)
        val htmlBody =
            FreeMarkerTemplateUtils.processTemplateIntoString(
                template,
                mapOf(
                    "order" to order,
                    "orderType" to translateOrderType(order.orderType)
                )
            )

        // Email Metadata
        setMailMetadata(
            helper = helper,
            htmlBody = htmlBody,
            subject = "Bestillingsbekreftelse fra WLS - ${order.hostOrderId}",
            from = senderEmail,
            to = receiver
        )
        return helper.mimeMessage
    }

    private fun translateOrderType(orderType: Order.Type): String =
        when (orderType) {
            Order.Type.LOAN -> "Lån"
            Order.Type.DIGITIZATION -> "Digitalisering"
        }

    private fun createOrderPickupMail(order: OrderEmail): MimeMessage? {
        if (storageEmail.isBlank()) {
            logger.error {
                "Sending order pickup mail to storage handlers is disabled"
            }
            return null
        }
        val mail = emailSender.createMimeMessage()
        val helper = MimeMessageHelper(mail, true)
        val type = "order.ftl"
        val template = freeMarkerConfigurer.configuration.getTemplate(type)
        val emailOrderItems = computeItemsWithQrCodes(order)
        val htmlBody =
            FreeMarkerTemplateUtils.processTemplateIntoString(
                template,
                mapOf(
                    "order" to order,
                    "orderQrCode" to getQrHtmlString(order.hostOrderId),
                    "orderItems" to emailOrderItems,
                    "orderType" to translateOrderType(order.orderType)
                )
            )
        setMailMetadata(
            helper = helper,
            htmlBody = htmlBody,
            subject = "Ny bestilling fra ${order.hostName} - ${order.hostOrderId}",
            from = order.contactEmail?.ifBlank { null },
            to = storageEmail
        )
        // QR-Code image handling for order ID and order items
        val orderIdQrImage = BarcodeUtils.createQrImage(order.hostOrderId, scale = 3, border = 4)
        helper.rootMimeMultipart.addBodyPart(createImagePart(orderIdQrImage, order.hostOrderId))
        for (orderItem in emailOrderItems) {
            helper.rootMimeMultipart.addBodyPart(createImagePart(orderItem.image, orderItem.item.hostId))
        }
        return helper.mimeMessage
    }

    private fun setMailMetadata(
        helper: MimeMessageHelper,
        htmlBody: String,
        subject: String,
        from: String?,
        to: String
    ) {
        helper.setText(htmlBody, true)
        helper.setSubject(subject)
        helper.setFrom(from ?: senderEmail)
        helper.setTo(to)
    }

    private fun createImagePart(
        image: BufferedImage,
        cid: String
    ): MimeBodyPart {
        val imagePart = MimeBodyPart()
        imagePart.disposition = MimeBodyPart.INLINE
        imagePart.contentID = "qr-${sanitizeID(cid)}"
        imagePart.dataHandler = BarcodeUtils.createImageDataHandler(image)
        return imagePart
    }

    private fun computeItemsWithQrCodes(orderEmail: OrderEmail): List<EmailOrderItem> {
        val list = mutableListOf<EmailOrderItem>()
        for (item in orderEmail.orderLines) {
            list.add(EmailOrderItem(item, getQrHtmlString(item.hostId), BarcodeUtils.createQrImage(item.hostId)))
        }
        return list
    }

    private fun getQrHtmlString(cid: String): String =
        """
            <img src="cid:qr-${sanitizeID(cid)}" alt="qrcode of '$cid'"/></img>
        """

    private fun sanitizeID(id: String): String = id.replace(Regex("[\\s<>\"'&]"), "_")

    data class EmailOrderItem(
        val item: OrderEmail.OrderLine,
        val qr: String,
        val image: BufferedImage
    )
}
