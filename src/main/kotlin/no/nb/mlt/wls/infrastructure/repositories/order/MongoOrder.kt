package no.nb.mlt.wls.infrastructure.repositories.order

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "orders")
@CompoundIndex(unique = true, def = "{'hostName':1,'hostOrderId':1}")
data class MongoOrder(
    @Id
    private val id: ObjectId = ObjectId(),
    val hostName: HostName,
    val hostOrderId: String,
    val status: Order.Status,
    val orderLine: List<Order.OrderItem>,
    val orderType: Order.Type,
    val contactPerson: String,
    val contactEmail: String?,
    val address: Order.Address?,
    val note: String?,
    val callbackUrl: String
)

fun Order.toMongoOrder(): MongoOrder =
    MongoOrder(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status,
        orderLine = orderLine,
        orderType = orderType,
        contactPerson = contactPerson,
        contactEmail = contactEmail,
        address = address,
        note = note,
        callbackUrl = callbackUrl
    )

fun MongoOrder.toOrder(): Order =
    Order(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status,
        orderLine = orderLine,
        orderType = orderType,
        contactPerson = contactPerson,
        contactEmail = contactEmail,
        address = address,
        note = note,
        callbackUrl = callbackUrl
    )
