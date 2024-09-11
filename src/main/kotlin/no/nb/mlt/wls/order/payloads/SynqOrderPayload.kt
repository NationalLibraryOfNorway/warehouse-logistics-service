package no.nb.mlt.wls.order.payloads

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.validation.constraints.Min
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.infrastructure.synq.SynqOwner
import no.nb.mlt.wls.infrastructure.synq.toSynqOwner
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

    enum class SynqOrderType(private val type: String) {
        STANDARD("Standard");

        @JsonValue
        override fun toString(): String {
            return type
        }
    }
}

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

fun Order.Type.toSynqOrderType(): SynqOrderPayload.SynqOrderType =
    when (this) {
        Order.Type.LOAN -> SynqOrderPayload.SynqOrderType.STANDARD // TODO: Since mock api defined more types than Synq has we map both to standard
        Order.Type.DIGITIZATION -> SynqOrderPayload.SynqOrderType.STANDARD
    }

/**
 * Utility classed used to wrap the payload.
 * This is required for SynQ's specification of handling orders
 */
data class SynqOrder(val order: List<SynqOrderPayload>)
