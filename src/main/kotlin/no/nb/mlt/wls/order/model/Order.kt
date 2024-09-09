package no.nb.mlt.wls.order.model

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.HostName
import no.nb.mlt.wls.domain.Owner
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

@CompoundIndex(unique = true, def = "{'hostName':1,'hostOrderId':1}")
@Document(collection = "orders")
data class Order(
    @Id
    private val id: ObjectId = ObjectId(),
    val hostName: HostName,
    val hostOrderId: String,
    val status: OrderStatus,
    val productLine: List<ProductLine>,
    val orderType: OrderType,
    val owner: Owner?,
    val receiver: OrderReceiver,
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
    val hostId: String,
    @Schema(
        description = "Current status of the order line.",
        examples = ["NOT_STARTED", "PICKED", "FAILED"],
        defaultValue = "NOT_STARTED",
        accessMode = READ_ONLY
    )
    val status: OrderLineStatus?
)

@Schema(
    description = "Information about order recipient.",
    example = """
    {
      "name": "Doug Dimmadome",
      "location": "Doug Dimmadome's office in the Dimmsdale Dimmadome",
      "address": "Dimmsdale Dimmadome",
      "city": "Dimmsdale",
      "postalCode": "69-420",
      "phoneNum": "+47 666 69 420"
    }
    """
)
data class OrderReceiver(
    @Schema(
        description = "Name of the recipient.",
        example = "Doug Dimmadome"
    )
    val name: String,
    @Schema(
        description = "Recipient's location, e.g. building, room, etc.",
        example = "Doug Dimmadome's office in the Dimmsdale Dimmadome"
    )
    val location: String,
    @Schema(
        description = "Recipient's address, e.g. street address, PO box, etc.",
        example = "Dimmsdale Dimmadome"
    )
    val address: String?,
    @Schema(
        description = "Recipient's city.",
        example = "Dimmsdale"
    )
    val city: String?,
    @Schema(
        description = "Recipient's postal code.",
        example = "69-420"
    )
    val postalCode: String?,
    @Schema(
        description = "Recipient's phone number, should contain country code, especially if it's a foreign number.",
        example = "+47 666 69 420"
    )
    val phoneNumber: String?
)

enum class OrderStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    DELETED
}

enum class OrderLineStatus {
    NOT_STARTED,
    PICKED,
    FAILED
}

enum class OrderType {
    LOAN,
    DIGITIZATION
}
