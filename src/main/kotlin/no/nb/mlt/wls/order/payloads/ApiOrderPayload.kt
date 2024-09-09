package no.nb.mlt.wls.order.payloads

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.HostName
import no.nb.mlt.wls.domain.Owner
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
      "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "hostName": "AXIELL",
      "hostOrderId": "mlt-12345-order",
      "status": "NOT_STARTED",
      "productLine": [
        {
          "hostId": "mlt-12345",
          "status": "NOT_STARTED"
        }
      ],
      "orderType": "LOAN",
      "owner": "NB",
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
data class ApiOrderPayload(
    // TODO: I don't see why we need this, but it was in the mock API
    @Schema(
        description = "Order ID in the database(?)",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        accessMode = READ_ONLY
    )
    val orderId: String,
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
        description = "Current status of the order as a whole.",
        examples = [ "NOT_STARTED", "IN_PROGRESS", "COMPLETED", "DELETED" ]
    )
    val status: OrderStatus?,
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
        description = "Who's the owner of the material in the order.",
        examples = [ "NB", "ARKIVVERKET" ],
        accessMode = READ_ONLY
    )
    val owner: Owner?,
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
        orderId = hostOrderId,
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status,
        productLine = productLine,
        orderType = orderType,
        owner = owner,
        receiver = receiver,
        callbackUrl = callbackUrl
    )

fun throwIfInvalidPayload(payload: ApiOrderPayload) {
    if (payload.orderId.isBlank()) {
        throw ServerWebInputException("The order's orderId is required, and can not be blank")
    }
    if (payload.hostOrderId.isBlank()) {
        throw ServerWebInputException("The order's hostOrderId is required, and can not be blank")
    }
    if (payload.productLine.isEmpty()) {
        throw ServerWebInputException("The order must contain product lines")
    }
}
