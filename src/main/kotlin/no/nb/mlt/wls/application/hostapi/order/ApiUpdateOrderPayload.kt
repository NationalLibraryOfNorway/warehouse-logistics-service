package no.nb.mlt.wls.application.hostapi.order

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.apache.commons.validator.routines.UrlValidator
import kotlin.jvm.Throws

@Schema(
    description = """Payload for editing an order in Hermes WLS, and appropriate storage system(s).""",
    example = """
    {
      "hostName": "AXIELL",
      "hostOrderId": "mlt-12345-order",
      "orderLine": [
        {
          "hostId": "mlt-12345",
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
      "callbackUrl": "https://callback-wls.no/order"
    }
    """
)
data class ApiUpdateOrderPayload(
    @Schema(
        description = """Name of the host system which made the order.""",
        examples = [ "AXIELL", "ALMA", "ASTA", "BIBLIOFIL" ]
    )
    val hostName: HostName,
    @Schema(
        description = """ID for the order, preferably the same ID as the one in the host system.""",
        example = "mlt-12345-order"
    )
    val hostOrderId: String,
    @Schema(
        description = """List of items in the order, also called order lines."""
    )
    val orderLine: List<OrderLine>,
    @Schema(
        description = """Describes what type of order this is.
            "LOAN" means that the order is for borrowing items to external or internal users,
            usually meaning the items will be viewed, inspected, etc.
            "DIGITIZATION" means that the order is specifically for digitizing items,
            usually meaning that the order will be delivered to digitization workstation.""",
        examples = [ "LOAN", "DIGITIZATION" ]
    )
    val orderType: Order.Type,
    @Schema(
        description = """Who to contact in relation to the order in case of any problems/issues/questions.""",
        example = "Dr. Heinz Doofenshmirtz"
    )
    val contactPerson: String,
    @Schema(
        description = """Where to send emails with communication or updates regarding the order.""",
        example = "heinz@doofenshmir.tz"
    )
    val contactEmail: String?,
    @Schema(
        description = """Address for the order, can be used as additional way of keeping track of where the order went to.""",
        example = "{...}"
    )
    val address: Order.Address?,
    @Schema(
        description = """Notes regarding the order, such as delivery instructions, special requests, etc.""",
        example = "I need this order in four weeks, not right now."
    )
    val note: String?,
    @Schema(
        description = """This URL will be used for POSTing order updates to the host system.
            For example when order items get picked or the order is cancelled.""",
        example = "https://callback-wls.no/order"
    )
    val callbackUrl: String
) {
    @Throws(ValidationException::class)
    fun validate() {
        if (hostOrderId.isBlank()) {
            throw ValidationException("Updated order's hostOrderId is required, and can not be blank")
        }

        if (orderLine.isEmpty()) {
            throw ValidationException("Updated order must have at least one order line")
        }

        if (!isValidUrl(callbackUrl)) {
            throw ValidationException("Updated order's callback URL is required, and must be a valid URL")
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
