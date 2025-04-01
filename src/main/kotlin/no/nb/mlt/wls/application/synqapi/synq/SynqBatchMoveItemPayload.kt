package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.ValidationException

@Schema(
    description = """Payload with Product/Item movement updates from the SynQ storage system.""",
    example = """
    {
      "tuId" : "6942066642",
      "location" : "SYNQ_WAREHOUSE",
      "prevLocation" : "WS_PLUKKSENTER_1",
      "loadUnit" : [
        {
          "confidentialProduct" : false,
          "hostName" : "AXIELL",
          "productId" : "mlt-12345",
          "productOwner" : "NB",
          "productVersionId" : "Default",
          "quantityOnHand" : 1.0,
          "suspect" : false,
          "attributeValue" : [
            {
              "name" : "materialStatus",
              "value" : "Available"
            }
          ],
          "position" : {
            "xPosition" : 1,
            "yPosition" : 1,
            "zPosition" : 1
          }
        }
      ],
      "user" : "per.person@nb.no",
      "warehouse" : "Sikringsmagasin_2"
    }"""
)
data class SynqBatchMoveItemPayload(
    @Schema(
        description = """ID of the transport unit in the SynQ storage system.""",
        example = "6942066642"
    )
    val tuId: String,
    @Schema(
        description = """Current location of the transport unit and its contents in the SynQ storage system.""",
        example = "SYNQ_WAREHOUSE"
    )
    val location: String,
    @Schema(
        description = """Previous location of the transport unit and its contents in the SynQ storage system.""",
        example = "WS_PLUKKSENTER_1"
    )
    val prevLocation: String,
    @Schema(
        description = """List of products/items in the transport unit (referred to as load units in SynQ).
            Since we only have unique items an LU is equivalent to product.
            In usual warehouses you have multiple copies of the same product, so an LU can be a stack of products.""",
        example = "[{...}]"
    )
    val loadUnit: List<Product>,
    @Schema(
        description = """Who cause the load unit to move, can be system if that was an automatic action.""",
        example = "per.person@nb.no"
    )
    val user: String,
    @Schema(
        description = """Warehouse in which the TU moved.""",
        example = "Sikringsmagasin_2"
    )
    val warehouse: String
)

@Schema(
    description = """Information about a product/item (LU) in the transport unit (TU) in the SynQ storage system.""",
    example = """
    {
      "confidentialProduct" : false,
      "hostName" : "AXIELL",
      "productId" : "mlt-12345",
      "productOwner" : "NB",
      "productVersionId" : "Default",
      "quantityOnHand" : 1.0,
      "suspect" : false,
      "attributeValue" : [
        {
          "name" : "materialStatus",
          "value" : "Available"
        }
      ],
      "position" : {
        "xPosition" : 1,
        "yPosition" : 1,
        "zPosition" : 1
      }
    }"""
)
data class Product(
    @Schema(
        description = """Marks the product as confidential, meaning only people with special access can view, modify, or request the product.""",
        example = "false"
    )
    val confidentialProduct: Boolean,
    @Schema(
        description = """Name of the host system which the product belongs to.""",
        example = "AXIELL"
    )
    @NotBlank
    val hostName: String,
    @Schema(
        description = """Product ID from the host system, usually a barcode value or an equivalent ID.""",
        example = "mlt-12345"
    )
    @NotBlank
    val productId: String,
    @Schema(
        description = """Product's owner, usually the National Library of Norway (NB) or the National Archives of Norway (AV).""",
        example = "NB"
    )
    @NotBlank
    val productOwner: String,
    @Schema(
        description = """Product version ID in the storage system, seems to always have value "Default".""",
        example = "Default"
    )
    val productVersionId: String,
    @Schema(
        description = """Product quantity in the TU, SynQ uses doubles for quantity, however we convert it to integers.""",
        example = "1.0"
    )
    @PositiveOrZero
    val quantityOnHand: Int,
    @Schema(
        description = """Signifies the product is missing, damaged, or otherwise suspect, and it requires manual action from the operator.""",
        example = "false"
    )
    val suspect: Boolean,
    @Schema(
        description = """List of attributes for the product.""",
        example = "[{...}]"
    )
    val attributeValue: List<AttributeValue>,
    @Schema(
        description = """Position of the product in the TU, not used by us so this is pretty irrelevant.""",
        example = "{...}"
    )
    val position: Position
)

@Schema(
    description = """Represents a product's attribute.""",
    example = """
    {
      "name" : "materialStatus",
      "value" : "Available"
    }"""
)
data class AttributeValue(
    @Schema(
        description = """Name of the attribute.""",
        example = "materialStatus"
    )
    val name: String,
    @Schema(
        description = """Value of the attribute.""",
        example = "Available"
    )
    val value: String
) {
    @Throws(ValidationException::class)
    fun validate() {
        if (name.isBlank()) {
            throw ValidationException("Attribute name cannot be blank")
        }

        if (value.isBlank()) {
            throw ValidationException("Attribute value cannot be blank")
        }
    }
}

@Schema(
    description = """Represents position of the product in the TU.""",
    example = """
    {
      "xPosition" : 1,
      "yPosition" : 1,
      "zPosition" : 1
    }"""
)
data class Position(
    @Schema(
        description = """X position of the product in the TU.""",
        example = "1"
    )
    val xPosition: Int,
    @Schema(
        description = """Y position of the product in the TU.""",
        example = "1"
    )
    val yPosition: Int,
    @Schema(
        description = """Z position of the product in the TU.""",
        example = "1"
    )
    val zPosition: Int
)

fun Product.toPayload(location: String): MoveItemPayload =
    MoveItemPayload(
        hostName = HostName.valueOf(hostName.uppercase()),
        hostId = productId,
        quantity = quantityOnHand,
        location = location
    )

fun SynqBatchMoveItemPayload.mapToItemPayloads(): List<MoveItemPayload> =
    loadUnit.map {
        it.toPayload(location)
    }
