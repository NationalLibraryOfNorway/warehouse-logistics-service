package no.nb.mlt.wls.application.logisticsapi

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Order.Address
import no.nb.mlt.wls.domain.model.Order.OrderItem
import no.nb.mlt.wls.domain.model.Order.Status
import no.nb.mlt.wls.domain.model.Order.Type
import no.nb.mlt.wls.domain.model.Packaging
import org.hibernate.validator.constraints.URL

@Schema(
    description = """Payload representing an order with items within Hermes WLS.""",
    example = """
    {
      "hostName": "AXIELL",
      "hostOrderId": "mlt-12345-order",
      "status": "NOT_STARTED",
      "orderLine": [
        {
          "hostId": "mlt-12345",
          "hostName": "AXIELL",
          "description": "Tyven, tyven skal du hete",
          "itemCategory": "PAPER",
          "preferredEnvironment": "NONE",
          "packaging": "NONE",
          "callbackUrl": "http://callback-wls.no/item",
          "location": "SYNQ_WAREHOUSE",
          "quantity": 1,
          "status": "NOT_STARTED"
        },
        {
          "hostId": "mlt-67890",
          "hostName": "AXIELL",
          "description": "Tyven, tyven skal du hete",
          "itemCategory": "PAPER",
          "preferredEnvironment": "NONE",
          "packaging": "NONE",
          "callbackUrl": "http://callback-wls.no/item",
          "location": "UNKNOWN",
          "quantity": 0,
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
data class ApiDetailedOrder(
    @field:Schema(
        description = """Name of the host system which made the order.""",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
    )
    @field:NotNull(message = "The order's host name is required, and can not be blank")
    val hostName: HostName,
    @field:Schema(
        description = """ID for the order, preferably the same ID as the one in the host system.""",
        example = "mlt-12345-order"
    )
    @field:NotBlank(message = "The order's hostOrderId is required, and can not be blank")
    val hostOrderId: String,
    @field:Schema(
        description = """Current status for the whole order.
            "COMPLETED" means that the order is finished and items are ready for pickup / shipping to receiver.
            "RETURNED" means that the order items have been returned to the storage.""",
        examples = ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "RETURNED", "DELETED"]
    )
    val status: Status,
    @field:Schema(
        description = """List of items in the order, also called order lines.""",
        accessMode = READ_ONLY
    )
    @field:Valid
    @field:NotEmpty(message = "The order must have at least one order line")
    val orderLine: List<DetailedOrderItem>,
    @field:Schema(
        description = """Describes what type of order this is.
            "LOAN" means that the order is for borrowing items to external or internal users,
            usually meaning the items will be viewed, inspected, etc.
            "DIGITIZATION" means that the order is specifically for digitizing items,
            usually meaning that the order will be delivered to digitization workstation.""",
        examples = ["LOAN", "DIGITIZATION"]
    )
    val orderType: Type,
    @field:Valid
    val address: Address?,
    @field:Schema(
        description = """Who to contact in relation to the order in case of any problems/issues/questions.""",
        example = "Dr. Heinz Doofenshmirtz"
    )
    @field:NotBlank(message = "The order's contactPerson is required, and can not be blank")
    val contactPerson: String,
    @field:Schema(
        description = """Where to send emails with communication or updates regarding the order.""",
        example = "heinz@doofenshmir.tz"
    )
    @field:Email(message = "Provided email address is not valid")
    val contactEmail: String?,
    @field:Schema(
        description = """Notes regarding the order, such as delivery instructions, special requests, etc.""",
        example = "I need this order in four weeks, not right now."
    )
    val note: String?,
    @field:NotBlank(message = "The callback URL is required, and can not be blank")
    @field:URL(message = "The callback URL must be a valid URL")
    @field:Pattern(regexp = "^(http|https)://.*$", message = "The URL must start with http:// or https://")
    val callbackUrl: String
)

@Schema(
    description = """Part of a detailed order payload. Represents an item in Hermes WLS with its order status.""",
    example = """
    {
      "hostId": "mlt-12345",
      "hostName": "AXIELL",
      "description": "Tyven, tyven skal du hete",
      "itemCategory": "PAPER",
      "preferredEnvironment": "NONE",
      "packaging": "NONE",
      "callbackUrl": "http://callback-wls.no/item",
      "location": "SYNQ_WAREHOUSE",
      "quantity": 1,
      "status": "NOT_STARTED"
    }
    """
)
data class DetailedOrderItem(
    @field:Schema(
        description = """The item ID from the host system, usually a barcode or any equivalent ID.""",
        example = "mlt-12345"
    )
    val hostId: String,
    @field:Schema(
        description = """Name of the host system that owns the item, and where the request comes from.
            Host system is usually the catalogue that the item is registered in.""",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
    )
    val hostName: HostName,
    @field:Schema(
        description = """Description of the item for easy identification in the warehouse system.
            Usually item's title/name, or contents description.""",
        examples = ["Tyven, tyven skal du hete", "Avisa Hemnes", "Kill Buljo", "Photo Collection, Hemnes, 2025-03-12"]
    )
    val description: String,
    @field:Schema(
        description = """Item's storage category or grouping.
            Items sharing a category can be stored together without any issues.
            For example: books and photo positives are of type PAPER, and can be stored without damaging each other.""",
        examples = ["PAPER", "DISC", "FILM", "PHOTO", "EQUIPMENT", "BULK_ITEMS", "MAGNETIC_TAPE"]
    )
    val itemCategory: ItemCategory,
    @field:Schema(
        description = """What kind of environment the item should be stored in.
            "NONE" means item has no preference for storage environment,
            "FREEZE" means that item should be stored frozen when stored, etc.
            NOTE: This is not a guarantee that the item will be stored in the preferred environment.
            In cases where storage space is limited, the item may be stored in regular environment.""",
        examples = ["NONE", "FREEZE"]
    )
    val preferredEnvironment: Environment,
    @field:Schema(
        description = """Whether the item is a single object or a container with other items inside.
            "NONE" is for single objects, "ABOX" is for archival boxes, etc.
            NOTE: It is up to the catalogue to keep track of the items inside a container.""",
        examples = ["NONE", "BOX", "ABOX"]
    )
    val packaging: Packaging,
    @field:Schema(
        description = """This URL will be used for POSTing item updates to the host system.
            For example when item moves or changes quantity in storage.""",
        example = "http://callback-wls.no/item"
    )
    val callbackUrl: String?,
    @field:Schema(
        description = """Last known storage location of the item.
            Can be used for tracking item movement through storage systems.""",
        examples = ["UNKNOWN", "WITH_LENDER", "SYNQ_WAREHOUSE", "AUTOSTORE", "KARDEX"]
    )
    val location: String,
    @field:Schema(
        description = """Quantity on hand of the item, this denotes if the item is in the storage or not.
            Quantity of 1 means the item is in storage, quantity of 0 means the item is not in storage.""",
        examples = ["0", "1"]
    )
    val quantity: Int,
    @field:Schema(
        description = """Current status for the ordered item.""",
        examples = ["NOT_STARTED", "PICKED", "RETURNED", "MISSING", "FAILED"],
        accessMode = READ_ONLY
    )
    val status: OrderItem.Status
)

fun Order.toDetailedOrder(items: List<Item>): ApiDetailedOrder {
    val detailedOrderItems = mutableListOf<DetailedOrderItem>()
    items.forEach { item ->
        this.orderLine.forEach { orderItem ->
            if (orderItem.hostId == item.hostId) {
                detailedOrderItems.add(orderItem.toDetailedOrderItem(item))
            }
        }
    }

    return ApiDetailedOrder(
        hostName = this.hostName,
        hostOrderId = this.hostOrderId,
        status = this.status,
        orderLine = detailedOrderItems.toList(),
        orderType = this.orderType,
        address = this.address,
        contactPerson = this.contactPerson,
        contactEmail = this.contactEmail,
        note = this.note,
        callbackUrl = this.callbackUrl
    )
}

fun OrderItem.toDetailedOrderItem(item: Item): DetailedOrderItem =
    DetailedOrderItem(
        hostId = item.hostId,
        hostName = item.hostName,
        description = item.description,
        itemCategory = item.itemCategory,
        preferredEnvironment = item.preferredEnvironment,
        packaging = item.packaging,
        callbackUrl = item.callbackUrl,
        location = item.location,
        quantity = item.quantity,
        status = this.status
    )
