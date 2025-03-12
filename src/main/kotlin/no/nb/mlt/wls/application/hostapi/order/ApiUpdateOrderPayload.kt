package no.nb.mlt.wls.application.hostapi.order

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
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
        description = """Order ID from the host system which made the order.""",
        example = "mlt-12345-order"
    )
    val hostOrderId: String,
    @Schema(
        description = """List of items in the order, also called order lines."""
    )
    val orderLine: List<OrderLine>,
    @Schema(
        description = """Describes what type of order this is""",
        examples = [ "LOAN", "DIGITIZATION" ]
    )
    val orderType: Order.Type,
    @Schema(
        description = """Who to contact in relation to the order if case of any problems/issues/questions.""",
        example = "Dr. Heinz Doofenshmirtz"
    )
    val contactPerson: String,
    @Schema(
        description = """Email address to send emails with communication or updates regarding the order.""",
        example = "heinz@Doofenshmir.tz"
    )
    val contactEmail: String?,
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
        description = """URL to send a callback to when the order is completed.""",
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
