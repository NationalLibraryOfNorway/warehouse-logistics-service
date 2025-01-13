package no.nb.mlt.wls.infrastructure.email

import jakarta.mail.internet.MimeMessage
import no.nb.mlt.wls.domain.model.Order
import org.springframework.mail.javamail.JavaMailSender

class EmailStructures {
    companion object {
        fun createLoanMessage(
            order: Order,
            emailSender: JavaMailSender
        ): MimeMessage {
            val mimeMessage = emailSender.createMimeMessage()

            val msg =
                """
                En ny bestilling har blitt mottatt for ${order.hostName}
                Type: ${order.orderType}
                Ordrelinjer: ${order.orderLine.map { it.hostId }}

                Bestiller: ${order.contactPerson}
                Melding fra bestiller: ${order.note}

                Referansenummer: ${order.hostOrderId}
                """.trimMargin()

            mimeMessage.setText(msg)
            mimeMessage.setSubject("Bestillingsbekreftelse fra WLS - ${order.hostOrderId}")
            mimeMessage.setFrom("noreply@nb.no")

            return mimeMessage
        }
    }
}
