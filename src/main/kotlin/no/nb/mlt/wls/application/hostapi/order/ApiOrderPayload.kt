package no.nb.mlt.wls.application.hostapi.order

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.hibernate.validator.constraints.URL


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
data class ApiOrderPayload(
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
        description = "List of items in the order, also called order lines.",
        accessMode = READ_ONLY
    )
    val orderLine: List<Order.OrderItem>,
    @Schema(
        description = "Describes what type of order this is",
        examples = ["LOAN", "DIGITIZATION"]
    )
    val orderType: Order.Type,
    @Schema(
        description = "Who's the owner of the material in the order.",
        examples = ["NB", "ARKIVVERKET"],
        accessMode = READ_ONLY
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
    @URL
    val callbackUrl: String
)

fun Order.toApiOrderPayload() =
    ApiOrderPayload(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status,
        orderLine = orderLine,
        orderType = orderType,
        owner = owner,
        receiver = receiver,
        callbackUrl = callbackUrl
    )

fun ApiOrderPayload.toOrder() =
    Order(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status ?: Order.Status.NOT_STARTED,
        orderLine =
            orderLine.map {
                Order.OrderItem(it.hostId, Order.OrderItem.Status.NOT_STARTED)
            },
        orderType = orderType,
        owner = owner,
        receiver = receiver,
        callbackUrl = callbackUrl
    )

fun Order.OrderItem.validate() {
    if (hostId.isBlank()) {
        throw ValidationException("The order item's hostId is required, and can not be blank")
    }
}

fun ApiOrderPayload.validate() {
    if (hostOrderId.isBlank()) {
        throw ValidationException("The order's hostOrderId is required, and can not be blank")
    }

    if (orderLine.isEmpty()) {
        throw ValidationException("The order must contain order lines")
    }

    orderLine.forEach{it::validate}
}
