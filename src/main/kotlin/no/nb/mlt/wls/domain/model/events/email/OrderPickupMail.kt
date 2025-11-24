package no.nb.mlt.wls.domain.model.events.email

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
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
