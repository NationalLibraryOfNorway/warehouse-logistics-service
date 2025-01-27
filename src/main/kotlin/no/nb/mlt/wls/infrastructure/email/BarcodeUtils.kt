package no.nb.mlt.wls.infrastructure.email

import io.nayuki.qrcodegen.QrCode
import jakarta.activation.DataHandler
import jakarta.mail.util.ByteArrayDataSource
import org.springframework.http.MediaType.IMAGE_PNG_VALUE
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class BarcodeUtils {
    companion object {
        private fun qrFromString(string: String): QrCode = QrCode.encodeText(string, QrCode.Ecc.HIGH)

        fun createQrImage(qrString: String): BufferedImage = createQrImage(qrFromString(qrString))

        fun createQrImage(
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

        fun createImageDataHandler(image: BufferedImage): DataHandler {
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            val source = ByteArrayDataSource(outputStream.toByteArray(), IMAGE_PNG_VALUE)
            return DataHandler(source)
        }
    }
}
