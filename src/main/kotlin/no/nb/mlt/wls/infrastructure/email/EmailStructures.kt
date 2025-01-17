package no.nb.mlt.wls.infrastructure.email

import jakarta.mail.internet.MimeMessage
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import org.springframework.mail.javamail.JavaMailSender

class EmailStructures {
    companion object {
        fun loanMessage(
            order: Order,
            orderItems: List<Item>,
            emailSender: JavaMailSender
        ): MimeMessage {
            val mimeMessage = emailSender.createMimeMessage()

            var orderLineStr = "\n"
            for (item in orderItems) {
                // TODO - Quantity needed for edge-cases
                // TODO - QR Code for Item ID
                orderLineStr +=
                    """
                    Objekt: ${item.hostId}
                    Beskrivelse: ${item.description}
                    Plassering: ${item.location}
                    Antall: 1
                    Destinasjon: ${computeDestination(item.itemCategory)}

                    """.trimIndent().plus("\n")
            }

            val msg =
                """
                |En ny bestilling har blitt mottatt fra ${order.hostName}
                |Type: ${order.orderType}
                |
                |Bestiller: ${order.contactPerson}
                |Melding fra bestiller: ${order.note}
                |
                |Referansenummer: ${order.hostOrderId}
                |
                |---
                |Ordrelinjer
                |---
                """.trimMargin().plus(orderLineStr)

            mimeMessage.setText(msg)
            mimeMessage.setSubject("Ny bestilling fra ${order.hostName} - ${order.hostOrderId}")
            mimeMessage.setFrom("noreply@nb.no")

            return mimeMessage
        }

        fun hostLoanConfirmation(
            order: Order,
            emailSender: JavaMailSender
        ): MimeMessage {
            val mimeMessage = emailSender.createMimeMessage()

            val msg =
                """
                |Bestillingen din har blitt mottatt.
                |
                |Type: ${order.orderType}
                |Ordrelinjer: ${order.orderLine.map { it.hostId }}
                |
                |Bestiller: ${order.contactPerson}
                |Melding fra bestiller: ${order.note}
                |
                |Referansenummer: ${order.hostOrderId}
                """.trimMargin()

            mimeMessage.setText(msg)
            mimeMessage.setSubject("Bestillingsbekreftelse fra WLS - ${order.hostOrderId}")
            mimeMessage.setFrom("noreply@nb.no")

            return mimeMessage
        }

        fun computeDestination(type: ItemCategory) {
            // FIXME - This is not right...
            when (type) {
                ItemCategory.PAPER -> TODO()
                ItemCategory.DISC -> TODO()
                ItemCategory.FILM -> TODO()
                ItemCategory.MAGNETIC_TAPE -> TODO()
                ItemCategory.PHOTO -> TODO()
                ItemCategory.EQUIPMENT -> TODO()
                ItemCategory.BULK_ITEMS -> TODO()
            }
        }
    }
}
