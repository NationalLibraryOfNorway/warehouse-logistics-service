package no.nb.mlt.wls.infrastructure.email

import io.github.oshai.kotlinlogging.KotlinLogging
import io.nayuki.qrcodegen.QrCode
import jakarta.activation.DataHandler
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.util.ByteArrayDataSource
import no.nb.mlt.wls.domain.model.HostEmail
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EmailRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.IMAGE_PNG_VALUE
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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
    ): MimeMessage {
        val type = "order-confirmation.ftl"
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
        val type = "order.ftl"
        val template = freeMarkerConfigurer.configuration.getTemplate(type)
        val qrCodes = computeItemsWithQrCodes(orderItems)
        addInlinedImage(toBufferedImage(QrCode.encodeText(order.hostOrderId, QrCode.Ecc.HIGH)), order.hostOrderId, helper)
        val htmlBody =
            FreeMarkerTemplateUtils.processTemplateIntoString(
                template,
                mapOf(
                    "order" to order,
                    "orderQrCode" to getQrHtmlString(order.hostOrderId),
                    "orderItems" to qrCodes
                )
            )
        helper.setText(htmlBody, true)
        helper.setSubject("Ny bestilling fra ${order.hostName} - ${order.hostOrderId}")
        helper.setFrom("noreply@nb.no")
        helper.setTo(storageEmail)
        for (qrCode in qrCodes) {
            addInlinedImage(qrCode.image, qrCode.item.hostId, helper)
        }
        return helper.mimeMessage
    }

    private fun addInlinedImage(
        image: BufferedImage,
        cid: String,
        helper: MimeMessageHelper
    ) {
        val imagePart = MimeBodyPart()
        imagePart.disposition = MimeBodyPart.INLINE
        imagePart.contentID = "qr-$cid"
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "png", outputStream)
        val source = ByteArrayDataSource(outputStream.toByteArray(), IMAGE_PNG_VALUE)
        imagePart.dataHandler = DataHandler(source)
        helper.rootMimeMultipart.addBodyPart(imagePart)
    }

    private fun computeItemsWithQrCodes(items: List<Item>): List<EmailOrderItem> {
        val list = mutableListOf<EmailOrderItem>()
        for (item in items) {
            val qrcode = QrCode.encodeText(item.hostId, QrCode.Ecc.HIGH)
            list.add(EmailOrderItem(item, getQrHtmlString(item.hostId), toBufferedImage(qrcode)))
        }
        return list
    }

    private fun toBufferedImage(
        qr: QrCode,
        scale: Int = 4,
        border: Int = 4
    ): BufferedImage {
        if (scale <= 0) throw IllegalArgumentException("Scale and border must be non-negative")
        if (border <= 0) throw IllegalArgumentException("Scale and border must be non-negative")
        if (border >= Int.MAX_VALUE / 2 || qr.size + border * 2L > Int.MAX_VALUE / scale) {
            throw IllegalArgumentException(
                "Scale or border is too large"
            )
        }

        val size = (qr.size + border * 2) * scale
        val result = BufferedImage(size, size, TYPE_INT_RGB)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val isDark = qr.getModule(x / scale - border, y / scale - border)
                val color =
                    if (isDark) {
                        0xFFFFE0
                    } else {
                        0xAE4000
                    }
                result.setRGB(x, y, color)
            }
        }
        return result
    }

    private fun getQrHtmlString(cid: String): String =
        """
            <img src="cid:qr-$cid" alt="qrcode"/></img>
        """

    data class EmailOrderItem(val item: Item, val qr: String, val image: BufferedImage)
}
