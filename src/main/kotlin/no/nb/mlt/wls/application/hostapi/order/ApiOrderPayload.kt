package no.nb.mlt.wls.application.hostapi.order

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import no.nb.mlt.wls.application.validations.NullableNotBlank
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import org.hibernate.validator.constraints.URL

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
        },
        {
          "hostId": "mlt-67890",
          "status": "NOT_STARTED"
        }
      ],
      "orderType": "LOAN",
      "contactPerson": "Dr. Heinz Doofenshmirtz",
      "contactEmail": "heinz@doofenshmir.tz",
      "address": {
        "recipient": "Doug Dimmadome",
        "addressLine1": "Dimmsdale Dimmadome",
        "addressLine2": "21st Texan Ave.",
        "city": "Dimmsdale",
        "country": "United States",
        "region": "California",
        "postcode": "CA-55415"
      },
      "note": "Handle with care",
      "callbackUrl": "http://callback-wls.no/order"
    }
    """
)
data class ApiOrderPayload(
    @Schema(
        description = """Name of the host system which made the order.""",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
    )
    @field:NotNull(message = "The order's hostOrderId is required, and can not be blank")
    val hostName: HostName,
    @Schema(
        description = """ID for the order, preferably the same ID as the one in the host system.""",
        example = "mlt-12345-order"
    )
    @field:NotBlank(message = "The order's hostOrderId is required, and can not be blank")
    val hostOrderId: String,
    @Schema(
        description = """Current status for the whole order.
            "COMPLETED" means that the order is finished and items are ready for pickup / shipping to receiver.
            "RETURNED" means that the order items have been returned to the storage.""",
        examples = ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "RETURNED", "DELETED"]
    )
    val status: Order.Status?,
    @Schema(
        description = """List of items in the order, also called order lines.""",
        accessMode = READ_ONLY
    )
    @field:Valid
    @field:NotEmpty(message = "The order must have at least one order line")
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
        description = """Who to contact in relation to the order in case of any problems/issues/questions.""",
        example = "Dr. Heinz Doofenshmirtz"
    )
    @field:NotBlank(message = "The order's contactPerson is required, and can not be blank")
    val contactPerson: String,
    @Schema(
        description = """Where to send emails with communication or updates regarding the order.""",
        example = "heinz@doofenshmir.tz"
    )
    @field:Email(message = "Provided email address is not valid")
    val contactEmail: String?,
    @Schema(
        description = """Address for the order, can be used as additional way of keeping track of where the order went to.""",
        example = "{...}"
    )
    @field:Valid
    val address: Address?,
    @Schema(
        description = """Notes regarding the order, such as delivery instructions, special requests, etc.""",
        example = "I need this order in four weeks, not right now."
    )
    val note: String?,
    @Schema(
        description = """This URL will be used for POSTing order updates to the host system.
            For example when order items get picked or the order is cancelled.""",
        example = "http://callback-wls.no/order"
    )
    @field:NotBlank(message = "The callback URL is required, and can not be blank")
    @field:URL(message = "The callback URL must be a valid URL")
    @field:Pattern(regexp = "^(http|https)://.*$", message = "The URL must start with http:// or https://")
    val callbackUrl: String
)

fun Order.toApiPayload() =
    ApiOrderPayload(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status,
        orderLine = orderLine.map { it.toApiOrderLine() },
        orderType = orderType,
        contactPerson = contactPerson,
        contactEmail = contactEmail,
        address =
            address?.let {
                Address(
                    recipient = it.recipient,
                    addressLine1 = it.addressLine1,
                    addressLine2 = it.addressLine2,
                    postcode = it.postcode,
                    city = it.city,
                    region = it.region,
                    country = it.country
                )
            },
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
    @field:NotBlank(message = "The order line's hostId is required, and can not be blank")
    val hostId: String,
    @Schema(
        description = """Current status for the ordered item.""",
        examples = ["NOT_STARTED", "PICKED", "RETURNED", "FAILED"],
        accessMode = READ_ONLY
    )
    val status: Order.OrderItem.Status?
) {
    fun toOrderItem() = Order.OrderItem(hostId, status ?: Order.OrderItem.Status.NOT_STARTED)

    fun toCreateOrderItem() = CreateOrderDTO.OrderItem(hostId)
}

data class Address(
    @field:NullableNotBlank(message = "Invalid address: recipient must not be blank if defined")
    val recipient: String?,
    @field:NullableNotBlank(message = "Invalid address: address line must not be blank if defined")
    val addressLine1: String?,
    @field:NullableNotBlank(message = "Invalid address: address line must not be blank if defined")
    val addressLine2: String?,
    @field:NullableNotBlank(message = "Invalid address: postcode must not be blank if defined")
    val postcode: String?,
    @field:NullableNotBlank(message = "Invalid address: city must not be blank if defined")
    val city: String?,
    @field:NullableNotBlank(message = "Invalid address: region must not be blank if defined")
    val region: String?,
    @field:NullableNotBlank(message = "Invalid address: country must not be blank if defined")
    val country: String?
)

fun Order.OrderItem.toApiOrderLine() = OrderLine(hostId, status)
