package no.nb.mlt.wls.application.hostapi.order

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
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
      "orderLine": [
        {
          "hostId": "mlt-12345"
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
      "note": "Handle with care",
      "callbackUrl": "https://callback-wls.no/order"
    }
    """
)
data class ApiCreateOrderPayload(
    @Schema(
        description = """Name of the host system which made the order.""",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
    )
    @field:NotNull
    val hostName: HostName,
    @Schema(
        description = """ID for the order, preferably the same ID as the one in the host system.""",
        example = "mlt-12345-order"
    )
    @field:NotEmpty(message = "The order's hostOrderId is required, and can not be blank")
    val hostOrderId: String,
    @Schema(
        description = """List of items in the order, also called order lines.""",
        accessMode = READ_ONLY
    )
    @field:NotEmpty(message = "The order must have at least one order line")
    val orderLine: List<@Valid OrderLine>,
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
    @field:NotEmpty(message = "The order's contactPerson is required, and can not be blank")
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
    @field:NotEmpty(message = "The callback URL is required, and can not be blank")
    @field:URL(message = "The callback URL must be a valid URL")
    @field:Pattern(regexp = "^(http|https)://.*$", message = "The URL must start with http:// or https://")
    val callbackUrl: String
) {
    fun toCreateOrderDTO() =
        CreateOrderDTO(
            hostName = hostName,
            hostOrderId = hostOrderId,
            orderLine = orderLine.map { it.toCreateOrderItem() },
            orderType = orderType,
            address = address,
            contactPerson = contactPerson,
            note = note,
            callbackUrl = callbackUrl
        )
}
