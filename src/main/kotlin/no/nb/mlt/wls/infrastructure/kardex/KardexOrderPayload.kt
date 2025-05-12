package no.nb.mlt.wls.infrastructure.kardex

import jakarta.validation.constraints.Min
import no.nb.mlt.wls.domain.model.Order

data class KardexOrderPayload(
    val orderName: String,
    val directionType: DirectionType,
    val priority: KardexPriority,
    val info5: String,
    val orderLines: List<OrderLine>
)

fun Order.toKardexOrderPayload(): KardexOrderPayload {
    return KardexOrderPayload(
        orderName = hostOrderId,
        directionType = DirectionType.Pick,
        priority = KardexPriority.Medium,
        info5 = hostName.toString(),
        orderLines =
            orderLine.mapIndexed { i, orderItem ->
                OrderLine(i, orderItem.hostId, 1.0)
            }
    )
}

enum class DirectionType {
    Put,
    Pick,
    Return,
    Transfer,
    Count,
    Production
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
