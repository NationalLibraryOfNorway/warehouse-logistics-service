package no.nb.mlt.wls.infrastructure.callbacks

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order

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
      "contactPerson": "Dr. Heinz Doofenshmirtz",
      "address": {
        "recipient": "Doug Dimmadome",
        "addressLine1": "Dimmsdale Dimmadome",
        "addressLine2": "21st Texan Ave.",
        "city": "Dimmsdale",
        "country": "United States",
        "region": "California",
        "postcode": "CA-55415"
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
        example = "NOT_STARTED"
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
        description = """Who to contact in relation to the order if case of any problems/issues/questions.""",
        example = "Dr. Heinz Doofenshmirtz"
    )
    val contactPerson: String,
    @Schema(
        description = """Address for the order, used in cases where storage operator sends out the order directly.""",
        example = "{...}"
    )
    val address: Order.Address?,
    @Schema(
        description = """Notes regarding the order, such as delivery instructions, special requests, etc.""",
        example = "I need this order in four weeks, not right now."
    )
    val note: String?,
    @Schema(
        description = """Callback URL to use for sending order updates to the host system.
            For example when order items get picked or the order is cancelled.""",
        example = "https://callback-wls.no/order"
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
        contactPerson = contactPerson,
        address = address,
        note = note,
        callbackUrl = callbackUrl
    )
