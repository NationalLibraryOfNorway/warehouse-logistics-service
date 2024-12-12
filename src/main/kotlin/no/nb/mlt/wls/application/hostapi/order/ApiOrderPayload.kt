package no.nb.mlt.wls.application.hostapi.order

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.apache.commons.validator.routines.UrlValidator

@Schema(
    description = """Payload for creating orders in Hermes WLS, and appropriate storage system(s).""",
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
      "contactPerson": "Hermes the Great",
      "address": {
        "recipient": "Nasjonalbibliotekaren",
        "addressLine1": "Henrik Ibsens gate 110",
        "city": "Oslo",
        "country": "Norway",
        "postcode": "0255"
      },
      "note": "Handle with care",
      "callbackUrl": "https://callback-wls.no/order"
    }
    """
)
data class ApiOrderPayload(
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
        accessMode = READ_ONLY
    )
    val orderLine: List<OrderLine>,
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
        description = "The name of the person to contact with manners related to this order",
        example = "Hermes"
    )
    val contactPerson: String,
    @Schema(
        description = "Any notes about the order",
        example = "This is required in four weeks time"
    )
    val note: String?,
    // TODO - Should this use custom DTO?
    @Schema(
        description = "The delivery address of this order",
        example = """
            "address": {
                "recipient": "Nasjonalbibliotekaren",
                "addressLine1": "Henrik Ibsens gate 110",
                "city": "Oslo",
                "country": "Norway",
                "postcode": "0255"
            }
        """
    )
    val address: Order.Address?,
    @Schema(
        description = """Callback URL to use for sending order updates in the host system.
            For example when order items get picked or the order is cancelled.""",
        example = "https://callback-wls.no/order"
    )
    val callbackUrl: String
) {
    fun toCreateOrderDTO(owner: Owner) =
        CreateOrderDTO(
            hostName = hostName,
            hostOrderId = hostOrderId,
            orderLine = orderLine.map { it.toCreateOrderItem() },
            orderType = orderType,
            owner = owner,
            address = address,
            contactPerson = contactPerson,
            note = note,
            callbackUrl = callbackUrl
        )

    @Throws(ValidationException::class)
    fun validate() {
        if (hostOrderId.isBlank()) {
            throw ValidationException("The order's hostOrderId is required, and can not be blank")
        }

        if (orderLine.isEmpty()) {
            throw ValidationException("The order must have at least one order line")
        }

        if (!isValidUrl(callbackUrl)) {
            throw ValidationException("The order's callback URL is required, and must be a valid URL")
        }

        orderLine.forEach(OrderLine::validate)
        address?.validate()
    }

    private fun isValidUrl(url: String): Boolean {
        // Yes I am aware that this function is duplicated in three places
        // But I prefer readability over DRY in cases like this

        val validator = UrlValidator(arrayOf("http", "https")) // Allow only HTTP/HTTPS
        return validator.isValid(url)
    }
}

fun Order.toApiOrderPayload() =
    ApiOrderPayload(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status,
        orderLine = orderLine.map { it.toApiOrderLine() },
        orderType = orderType,
        contactPerson = contactPerson,
        address = address,
        note = note,
        callbackUrl = callbackUrl
    )

@Schema(
    description = """Represents an order line in an order, containing information about the ordered item.""",
    example = """
    {
      "hostId": "mlt-12345",
      "status": "NOT_STARTED"
    }
    """
)
data class OrderLine(
    @Schema(
        description = """Item ID from the host system.""",
        example = "mlt-12345"
    )
    val hostId: String,
    @Schema(
        description = """Current status for the ordered item.""",
        examples = ["NOT_STARTED", "PICKED", "FAILED"]
    )
    val status: Order.OrderItem.Status?
) {
    fun toOrderItem() = Order.OrderItem(hostId, status ?: Order.OrderItem.Status.NOT_STARTED)

    fun toCreateOrderItem() = CreateOrderDTO.OrderItem(hostId)

    @Throws(ValidationException::class)
    fun validate() {
        if (hostId.isBlank()) {
            throw ValidationException("The order line's hostId is required, and can not be blank")
        }
    }
}

fun Order.OrderItem.toApiOrderLine() = OrderLine(hostId, status)
