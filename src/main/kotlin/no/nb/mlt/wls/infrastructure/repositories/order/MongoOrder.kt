package no.nb.mlt.wls.infrastructure.repositories.order

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

@CompoundIndex(unique = true, def = "{'hostName':1,'hostOrderId':1}")
@Document(collection = "orders")
data class MongoOrder(
    @Id
    private val id: ObjectId = ObjectId(),
    val hostName: HostName,
    val hostOrderId: String,
    val status: Order.Status,
    val productLine: List<Order.OrderItem>,
    val orderType: Order.Type,
    val owner: Owner?,
    val receiver: Order.Receiver,
    val callbackUrl: String
)
