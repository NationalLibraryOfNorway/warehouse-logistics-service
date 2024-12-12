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
      "contactPerson": "MLT Team",
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
      "callbackUrl": "https://example.com/send/callback/here"
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
        description = """List of items in the order, also called order lines.""",
        accessMode = READ_ONLY
    )
    val orderLine: List<OrderLine>,
    @Schema(
        description = """Describes what type of order this is""",
        examples = [ "LOAN", "DIGITIZATION" ]
    )
    val orderType: Order.Type,
    @Schema(
        description = """Who's the receiver of the material in the order.""",
        example = "{...}"
    )
    val contactPerson: String,
    @Schema(
        description = """
            The delivery address of the order.
            If delivering to a country with states (I.E. the United States), include the state name in the region.
        """,
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
        description = "Any notes about the order",
        example = "This is required in four weeks time"
    )
    val note: String?,
    @Schema(
        description = """URL to send a callback to when the order is completed.""",
        example = "https://example.com/send/callback/here"
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
