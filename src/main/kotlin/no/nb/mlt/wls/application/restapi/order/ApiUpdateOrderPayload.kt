package no.nb.mlt.wls.application.restapi.order

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import org.springframework.web.server.ServerWebInputException
import kotlin.jvm.Throws

@Schema(
    description = "Payload for creating and editing an order in Hermes WLS, and appropriate storage system(s).",
    example = """
    {
      "hostName": "AXIELL",
      "hostOrderId": "mlt-12345-order",
      "productLine": [
        {
          "hostId": "mlt-12345",
          "status": "NOT_STARTED"
        }
      ],
      "orderType": "LOAN",
      "receiver": {
        "name": "Doug Dimmadome",
        "address": "Dimmsdale Dimmadome, 21st Ave. Texas"
      },
      "callbackUrl": "https://example.com/send/callback/here"
    }
    """
)
data class ApiUpdateOrderPayload(
    @Schema(
        description = "Name of the host system which made the order.",
        examples = [ "AXIELL", "ALMA", "ASTA", "BIBLIOFIL" ]
    )
    val hostName: HostName,
    @Schema(
        description = "Order ID from the host system which made the order.",
        example = "mlt-12345-order"
    )
    val hostOrderId: String,
    @Schema(
        description = "List of products in the order, also called order lines, or product lines.",
        accessMode = READ_ONLY
    )
    val productLine: List<ProductLine>,
    @Schema(
        description = "Describes what type of order this is",
        examples = [ "LOAN", "DIGITIZATION" ]
    )
    val orderType: Order.Type,
    @Schema(
        description = "Who's the receiver of the material in the order."
    )
    val receiver: Order.Receiver,
    @Schema(
        description = "URL to send a callback to when the order is completed.",
        example = "https://example.com/send/callback/here"
    )
    val callbackUrl: String
)

@Schema(
    description = "Represents an order/product line in an order, containing information about ordered product.",
    example = """
    {
      "hostId": "mlt-12345",
      "status": "NOT_STARTED"
    }
    """
)
data class ProductLine(
    @Schema(
        description = "Product ID from the host system.",
        example = "mlt-12345"
    )
    val hostId: String
)

@Throws(ServerWebInputException::class)
fun ApiUpdateOrderPayload.throwIfInvalid() {
    if (this.hostOrderId.isBlank()) {
        throw ServerWebInputException("The order's hostOrderId is required, and can not be blank")
    }
    if (this.productLine.isEmpty()) {
        throw ServerWebInputException("The order must contain product lines")
    }
}
