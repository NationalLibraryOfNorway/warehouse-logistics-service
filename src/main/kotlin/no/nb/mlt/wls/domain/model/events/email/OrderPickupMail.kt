package no.nb.mlt.wls.domain.model.events.email

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.email.OrderPickupMail.OrderPickupData
import no.nb.mlt.wls.domain.model.events.email.OrderPickupMail.OrderPickupData.OrderLine
import java.util.*

data class OrderPickupMail(
    val orderPickupData: OrderPickupData,
    override val id: String = UUID.randomUUID().toString()
) : EmailEvent {
    override val body: Any
        get() = (orderPickupData)

    data class OrderPickupData(
        val hostOrderId: String,
        val hostName: HostName,
        val orderType: Order.Type,
        val contactPerson: String,
        val contactEmail: String?,
        val note: String?,
        val orderLines: List<OrderLine>
    ) {
        data class OrderLine(
            val hostId: String,
            val description: String,
            val location: String
        )
    }
}

fun createOrderPickupData(
    order: Order,
    items: List<Item>
): OrderPickupData =
    OrderPickupData(
        hostName = order.hostName,
        hostOrderId = order.hostOrderId,
        orderType = order.orderType,
        contactPerson = order.contactPerson,
        contactEmail = order.contactEmail,
        note = order.note,
        orderLines =
            items.map { item ->
                OrderLine(
                    hostId = item.hostId,
                    description = item.description,
                    location = item.location
                )
            }
    )
