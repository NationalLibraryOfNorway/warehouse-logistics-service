package no.nb.mlt.wls.application.hostapi.order

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import java.net.MalformedURLException
import java.net.URI

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
    val orderLine: List<OrderLine>,
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
    val receiver: Receiver,
    @Schema(
        description = "Callback URL for the order used to update the order information in the host system.",
        example = "https://example.com/send/callback/here"
    )
    val callbackUrl: String
) {
    fun toOrder() =
        Order(
            hostName = hostName,
            hostOrderId = hostOrderId,
            status = status ?: Order.Status.NOT_STARTED,
            orderLine = orderLine.map { it.toOrderItem() },
            orderType = orderType,
            owner = owner,
            receiver = receiver.toOrderReceiver(),
            callbackUrl = callbackUrl
        )

    fun toCreateOrderDTO() =
        CreateOrderDTO(
            hostName = hostName,
            hostOrderId = hostOrderId,
            orderLine = orderLine.map { it.toCreateOrderItem() },
            orderType = orderType,
            owner = owner,
            receiver = receiver.toOrderReceiver(),
            callbackUrl = callbackUrl
        )

    fun validate() {
        if (hostOrderId.isBlank()) {
            throw ValidationException("The order's hostOrderId is required, and can not be blank")
        }

        if (orderLine.isEmpty()) {
            throw ValidationException("The order must contain order lines")
        }

        if (!isValidUrl(callbackUrl)) {
            throw ValidationException("The order's callbackUrl is required, and must be a valid URL")
        }

        orderLine.forEach { it.validate() }
        receiver.validate()
    }

    private fun isValidUrl(url: String): Boolean {
        // Yes I am aware that this function is duplicated in three places
        // But I prefer readability over DRY in cases like this

        return try {
            URI(url).toURL() // Try to create a URL object
            true
        } catch (_: MalformedURLException) {
            false // If exception is thrown, it's not a valid URL
        }
    }
}

fun Order.toApiOrderPayload() =
    ApiOrderPayload(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status,
        orderLine = orderLine.map { it.toApiOrderLine() },
        orderType = orderType,
        owner = owner,
        receiver = Receiver(receiver.name, receiver.address),
        callbackUrl = callbackUrl
    )

@Schema(
    description = "Represents an order line in an order, containing information about ordered item.",
    example = """
    {
      "hostId": "mlt-12345",
      "status": "NOT_STARTED"
    }
    """
)
data class OrderLine(
    @Schema(
        description = "Item ID from the host system.",
        example = "mlt-12345"
    )
    val hostId: String,
    @Schema(
        description = "Current status of the item in the order.",
        examples = ["NOT_STARTED", "PICKED", "FAILED"]
    )
    val status: Order.OrderItem.Status?
) {
    fun toOrderItem() = Order.OrderItem(hostId, status ?: Order.OrderItem.Status.NOT_STARTED)

    fun toCreateOrderItem() = CreateOrderDTO.OrderItem(hostId)

    @Throws(ValidationException::class)
    fun validate() {
        if (hostId.isBlank()) {
            throw ValidationException("The order item's hostId is required, and can not be blank")
        }
    }
}

fun Order.OrderItem.toApiOrderLine() = OrderLine(hostId, status)

@Schema(
    description = "Who's the receiver of the order.",
    example = """
    {
      "name": "Doug Dimmadome",
      "address": "Dimmsdale Dimmadome, Apartment 420, 69th Ave. Texas"
    }
    """
)
data class Receiver(
    @Schema(
        description = "Name of the receiver.",
        example = "Doug Dimmadome"
    )
    val name: String,
    @Schema(
        description = "Address of the receiver.",
        example = "Dimmsdale Dimmadome, Apartment 420, 69th Ave. Texas",
        required = false
    )
    val address: String?
) {
    fun toOrderReceiver() = Order.Receiver(name, address ?: "")

    @Throws(ValidationException::class)
    fun validate() {
        if (name.isBlank()) {
            throw ValidationException("The order's receiver name is required, and can not be blank")
        }

        if (address != null && address.isBlank()) {
            throw ValidationException("The order's receiver address cannot be blank if provided")
        }
    }
}

fun Order.Receiver.toReceiver() = Receiver(name, address)
