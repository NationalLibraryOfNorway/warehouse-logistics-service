/*
* Adapted from https://github.com/nayuki/QR-Code-generator/blob/2c9044de6b049ca25cb3cd1649ed7e27aa055138/java/QrCodeGeneratorDemo.java
*
* Copyright (c) Project Nayuki. (MIT License)
* https://www.nayuki.io/page/qr-code-generator-library
*
* Permission is hereby granted, free of charge, to any person obtaining a copy of
* this software and associated documentation files (the "Software"), to deal in
* the Software without restriction, including without limitation the rights to
* use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
* the Software, and to permit persons to whom the Software is furnished to do so,
* subject to the following conditions:
* - The above copyright notice and this permission notice shall be included in
*   all copies or substantial portions of the Software.
* - The Software is provided "as is", without warranty of any kind, express or
*   implied, including but not limited to the warranties of merchantability,
*   fitness for a particular purpose and noninfringement. In no event shall the
*   authors or copyright holders be liable for any claim, damages or other
*   liability, whether in an action of contract, tort or otherwise, arising from,
*   out of or in connection with the Software or the use or other dealings in the
*   Software.
*/
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
            qrString: String,
            scale: Int = 4,
            border: Int = 4
        ): BufferedImage = createQrImage(qrFromString(qrString), scale, border)

        private fun createQrImage(
            qr: QrCode,
            scale: Int = 4,
            border: Int = 4
        ): BufferedImage {
            require(scale > 0) { "Scale must be non-negative" }
            require(border > 0) { "Border must be non-negative" }
            require(border < Int.MAX_VALUE / 2) { "Border is too large" }
            require(qr.size + border * 2L < Int.MAX_VALUE / scale) { "Scale of this QR does not fit inside border" }

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
