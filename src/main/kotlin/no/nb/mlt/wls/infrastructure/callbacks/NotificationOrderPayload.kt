package no.nb.mlt.wls.infrastructure.callbacks

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner

@Schema(
    description = """Payload for updates about orders sent from Hermes WLS to the appropriate catalogues.""",
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
      "callbackUrl": "https://callback-wls.no/order"
    }
    """
)
data class NotificationOrderPayload(
    @Schema(
        description = """Name of the host system which made the order.""",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
    )
    val hostName: HostName,
    @Schema(
        description = """ID for the order, preferably the same ID as the one in the host system.""",
        example = "mlt-12345-order"
    )
    val hostOrderId: String,
    @Schema(
        description = """Current status for the whole order.
            "COMPLETED" means that the order is finished and items are ready for pickup / sent to receiver.
            "RETURNED" means that the order items have been returned to the storage.""",
        examples = ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "RETURNED", "DELETED"]
    )
    val status: Order.Status?,
    @Schema(
        description = """List of items in the order, also called order lines.""",
    )
    val orderLine: List<Order.OrderItem>,
    @Schema(
        description = """Describes what type of order this is.
            "LOAN" means that the order is for borrowing items to external or internal users,
            usually meaning the items will be viewed, inspected, etc.
            "DIGITIZATION" means that the order is specifically for digitizing items,
            usually meaning that the order will be delivered to digitization workstation.""",
        examples = ["LOAN", "DIGITIZATION"]
    )
    val orderType: Order.Type,
    @Schema(
        description = """Who's the owner of the material in the order.""",
        examples = ["NB", "ARKIVVERKET"]
    )
    val owner: Owner?,
    @Schema(
        description = """Who's the receiver of the order."""
    )
    val receiver: Order.Receiver,
    @Schema(
        description = """Callback URL to use for sending order updates in the host system.
            For example when order items get picked or the order is cancelled.""",
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
