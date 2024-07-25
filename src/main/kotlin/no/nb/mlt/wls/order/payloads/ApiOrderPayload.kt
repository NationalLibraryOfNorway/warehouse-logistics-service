package no.nb.mlt.wls.order.payloads

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.order.model.Order
import no.nb.mlt.wls.order.model.OrderReceiver
import no.nb.mlt.wls.order.model.OrderStatus
import no.nb.mlt.wls.order.model.OrderType
import no.nb.mlt.wls.order.model.ProductLine

data class ApiOrderPayload(
    val hostName: HostName,
    val hostOrderId: String,
    val status: OrderStatus?,
    val productLine: List<ProductLine>,
    val orderType: OrderType,
    val owner: Owner?,
    val receiver: OrderReceiver,
    val callbackUrl: String
)

fun ApiOrderPayload.toOrder() =
    Order(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status ?: OrderStatus.NOT_STARTED,
        productLine = productLine,
        orderType = orderType,
        owner = owner,
        receiver = receiver,
        callbackUrl = callbackUrl
    )

fun Order.toApiOrderPayload() =
    ApiOrderPayload(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status,
        productLine = productLine,
        orderType = orderType,
        owner = owner,
        receiver = receiver,
        callbackUrl = callbackUrl
    )
