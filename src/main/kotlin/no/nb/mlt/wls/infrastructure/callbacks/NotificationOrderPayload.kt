package no.nb.mlt.wls.infrastructure.callbacks

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner

@Schema(
    description = "Payload for creating and editing an order in Hermes WLS, and appropriate storage system(s).",
    example = """
    {
      "hostName": "AXIELL",
      "hostOrderId": "mlt-12345-order",
      "status": "NOT_STARTED",
      "orderLine": [
        {
          "hostId": "mlt-12345",
          "status": "NOT_STARTED"
        }
      ],
      "orderType": "LOAN",
      "owner": "NB",
      "receiver": {
        "name": "Doug Dimmadome",
        "address": "Dimmsdale Dimmadome, 21st Ave. Texas"
      },
      "callbackUrl": "https://example.com/send/callback/here"
    }
    """
)
data class NotificationOrderPayload(
    @Schema(
        description = "Name of the host system which made the order.",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
    )
    val hostName: HostName,
    @Schema(
        description = "Order ID from the host system which made the order.",
        example = "mlt-12345-order"
    )
    val hostOrderId: String,
    @Schema(
        description = "Current status of the order as a whole.",
        examples = ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "DELETED"]
    )
    val status: Order.Status?,
    @Schema(
        description = "List of items in the order, also called order lines."
    )
    val orderLine: List<Order.OrderItem>,
    @Schema(
        description = "Describes what type of order this is",
        examples = ["LOAN", "DIGITIZATION"]
    )
    val orderType: Order.Type,
    @Schema(
        description = "Who's the owner of the material in the order.",
        examples = ["NB", "ARKIVVERKET"]
    )
    val owner: Owner?,
    @Schema(
        description = "Who's the receiver of the material in the order."
    )
    val receiver: Order.Receiver,
    @Schema(
        description = "Callback URL for the order used to update the order information in the host system.",
        example = "https://example.com/send/callback/here"
    )
    val callbackUrl: String
)

fun Order.toNotificationOrderPayload() =
    NotificationOrderPayload(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status,
        orderLine = orderLine,
        orderType = orderType,
        owner = owner,
        receiver = receiver,
        callbackUrl = callbackUrl
    )
