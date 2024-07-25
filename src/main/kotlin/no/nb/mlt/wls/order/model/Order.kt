package no.nb.mlt.wls.order.model

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "orders")
data class Order(
    val hostName: HostName,
    val hostOrderId: String,
    val status: OrderStatus,
    val productLine: List<ProductLine>,
    val orderType: OrderType,
    val owner: Owner?,
    val receiver: OrderReceiver,
    val callbackUrl: String
)

data class ProductLine(
    val hostId: String,
    val status: OrderStatus?
)

data class OrderReceiver(
    val name: String,
    val location: String,
    val address: String?,
    val city: String?,
    val postalCode: String?,
    val phoneNumber: String?
)

enum class OrderStatus(private val status: String) {
    NOT_STARTED("Not started"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    DELETED("Deleted");

    override fun toString(): String {
        return status
    }
}

enum class OrderType(private val type: String) {
    LOAN("Loan"),
    DIGITIZATION("Digitization") ;

    override fun toString(): String {
        return type
    }
}
