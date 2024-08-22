package no.nb.mlt.wls.order.payloads

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.Min
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.synq.SynqOwner
import no.nb.mlt.wls.core.data.synq.toOwner
import no.nb.mlt.wls.core.data.synq.toSynqOwner
import no.nb.mlt.wls.order.model.Order
import no.nb.mlt.wls.order.model.OrderReceiver
import no.nb.mlt.wls.order.model.OrderStatus
import no.nb.mlt.wls.order.model.OrderType
import no.nb.mlt.wls.order.model.ProductLine
import java.time.LocalDateTime

data class SynqOrderPayload(
    val orderId: String,
    val orderType: SynqOrderType,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val dispatchDate: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val orderDate: LocalDateTime,
    val priority: Int,
    val owner: SynqOwner,
    val orderLine: List<OrderLine>
) {
    data class OrderLine(
        @Min(1)
        val orderLineNumber: Int,
        val productId: String,
        val quantityOrdered: Double
    )

    enum class SynqOrderType {
        STANDARD
    }
}

fun SynqOrderPayload.toOrder() =
    Order(
        hostName = HostName.valueOf("SYNQ"),
        hostOrderId = orderId,
        // TODO: As I see we don't get status from SynQ, need to figure out what to do with it
        status = OrderStatus.NOT_STARTED,
        productLine =
            orderLine.map {
                ProductLine(
                    hostId = it.productId,
                    // TODO: Do we need to set it to something? Like: OrderStatus.NOT_STARTED?
                    status = null
                )
            },
        orderType = orderType.toOrderType(),
        // TODO: Need to get it from DB as we don't get it from SynQ
        receiver =
            OrderReceiver(
                name = "N/A",
                location = "N/A",
                address = null,
                city = null,
                postalCode = null,
                phoneNumber = null
            ),
        // TODO: Need to get if from DB as SynQ isn't aware of this field
        callbackUrl = "N/A",
        owner = owner.toOwner()
    )

fun Order.toSynqPayload() =
    SynqOrderPayload(
        orderId = hostOrderId,
        orderType = orderType.toSynqOrderType(),
        // When order should be dispatched, AFAIK it's not used by us as we don't receive orders in future
        dispatchDate = LocalDateTime.now(),
        // When order was made in SynQ, if we want to we can omit it and SynQ will set it to current date itself
        orderDate = LocalDateTime.now(),
        // TODO: we don't get it from API so we set it to 1, is other value more appropriate?
        priority = 1,
        owner = owner?.toSynqOwner() ?: SynqOwner.NB,
        orderLine =
            productLine.mapIndexed { index, it ->
                SynqOrderPayload.OrderLine(
                    orderLineNumber = index + 1,
                    productId = it.hostId,
                    quantityOrdered = 1.0
                )
            }
    )

fun SynqOrderPayload.SynqOrderType.toOrderType(): OrderType =
    when (this) {
        SynqOrderPayload.SynqOrderType.STANDARD -> OrderType.LOAN // TODO: Arbitrary mapping, need to discuss it with the team
    }

fun OrderType.toSynqOrderType(): SynqOrderPayload.SynqOrderType =
    when (this) {
        OrderType.LOAN -> SynqOrderPayload.SynqOrderType.STANDARD // TODO: Since mock api defined more types than Synq has we map both to standard
        OrderType.DIGITIZATION -> SynqOrderPayload.SynqOrderType.STANDARD
    }

// TODO - Improve this

/**
 * Utility classed used to wrap the payload.
 * This is required for SynQ's specification of handling orders
 */
data class SynqOrder(val order: List<SynqOrderPayload>)
