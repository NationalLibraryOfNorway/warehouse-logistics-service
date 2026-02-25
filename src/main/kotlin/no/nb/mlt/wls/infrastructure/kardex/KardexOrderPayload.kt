package no.nb.mlt.wls.infrastructure.kardex

import jakarta.validation.constraints.Min
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.DELIMITER

data class KardexOrderPayload(
    val orderName: String,
    val directionType: DirectionType,
    val priority: KardexPriority,
    val info5: String,
    val orderLines: List<OrderLine>
)

fun Order.toKardexOrderPayload(): KardexOrderPayload =
    KardexOrderPayload(
        orderName = hostName.toString() + DELIMITER + hostOrderId,
        directionType = DirectionType.Pick,
        priority = KardexPriority.Medium,
        info5 = hostName.toString(),
        orderLines =
            orderLine.mapIndexed { i, orderItem ->
                OrderLine(i, orderItem.hostId, 1.0)
            }
    )

enum class DirectionType {
    Put,
    Pick
}

enum class KardexPriority {
    Low,
    Medium,
    High,
    Hot
}

data class OrderLine(
    @field:Min(value = 1, message = "Order Line must start at 1")
    val lineNumber: Int,
    val materialName: String,
    @field:Min(value = 0)
    val quantity: Double
)
