package no.nb.mlt.wls.order.payloads

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.HostName
import no.nb.mlt.wls.order.model.Order
import no.nb.mlt.wls.order.model.OrderReceiver
import no.nb.mlt.wls.order.model.OrderStatus
import no.nb.mlt.wls.order.model.OrderType
import no.nb.mlt.wls.order.model.ProductLine
import org.springframework.web.server.ServerWebInputException

@Schema(
    description = "Payload for creating and editing an order in Hermes WLS, and appropriate storage system(s).",
    example = """
    {
      "hostName": "AXIELL",
      "hostOrderId": "mlt-12345-order",
      "productLine": [
        {
          "hostId": "mlt-12345",
          "status": "NOT_STARTED"
        }
      ],
      "orderType": "LOAN",
      "receiver": {
        "name": "Doug Dimmadome",
        "location": "Doug Dimmadome's office in the Dimmsdale Dimmadome",
        "address": "Dimmsdale Dimmadome",
        "city": "Dimmsdale",
        "postalCode": "69-420",
        "phoneNum": "+47 666 69 420"
      },
      "callbackUrl": "https://example.com/send/callback/here"
    }
    """
)
data class ApiUpdateOrderPayload(
    @Schema(
        description = "Name of the host system which made the order.",
        examples = [ "AXIELL", "ALMA", "ASTA", "BIBLIOFIL" ]
    )
    val hostName: HostName,
    @Schema(
        description = "Order ID from the host system which made the order.",
        example = "mlt-12345-order"
    )
    val hostOrderId: String,
    @Schema(
        description = "List of products in the order, also called order lines, or product lines.",
        accessMode = READ_ONLY
    )
    val productLine: List<ProductLine>,
    @Schema(
        description = "Describes what type of order this is",
        examples = [ "LOAN", "DIGITIZATION" ]
    )
    val orderType: OrderType,
    @Schema(
        description = "Who's the receiver of the material in the order."
    )
    val receiver: OrderReceiver,
    @Schema(
        description = "URL to send a callback to when the order is completed.",
        example = "https://example.com/send/callback/here"
    )
    val callbackUrl: String
)

fun ApiOrderPayload.toUpdateOrderPayload() =
    ApiUpdateOrderPayload(
        hostName = hostName,
        hostOrderId = hostOrderId,
        productLine = productLine,
        orderType = orderType,
        receiver = receiver,
        callbackUrl = callbackUrl
    )

fun ApiUpdateOrderPayload.toOrder() =
    Order(
        hostOrderId = hostOrderId,
        hostName = hostName,
        status = OrderStatus.NOT_STARTED,
        productLine = productLine,
        orderType = orderType,
        // TODO - validate if this is ok to do
        owner = null,
        receiver = receiver,
        callbackUrl = callbackUrl
    )

fun Order.toUpdateOrderPayload() =
    ApiUpdateOrderPayload(
        hostName = hostName,
        hostOrderId = hostOrderId,
        productLine = productLine,
        orderType = orderType,
        receiver = receiver,
        callbackUrl = callbackUrl
    )

fun throwIfInvalidPayload(payload: ApiUpdateOrderPayload) {
    if (payload.hostOrderId.isBlank()) {
        throw ServerWebInputException("The order's hostOrderId is required, and can not be blank")
    }
    if (payload.productLine.isEmpty()) {
        throw ServerWebInputException("The order must contain product lines")
    }
}
