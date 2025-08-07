package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem.UpdateItemPayload
import no.nb.mlt.wls.domain.ports.outbound.ItemMovingException

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
    @field:Schema(
        description = """ID of the transport unit in the SynQ storage system.""",
        example = "6942066642"
    )
    @field:NotBlank
    val tuId: String,
    @field:Schema(
        description = """Current location of the transport unit and its contents in the SynQ storage system.""",
        example = "SYNQ_WAREHOUSE"
    )
    @field:NotBlank
    val location: String,
    @field:Schema(
        description = """Previous location of the transport unit and its contents in the SynQ storage system.""",
        example = "WS_PLUKKSENTER_1"
    )
    val prevLocation: String,
    @field:Schema(
        description = """List of products/items in the transport unit (referred to as load units in SynQ).
            Since we only have unique items an LU is equivalent to product.
            In usual warehouses you have multiple copies of the same product, so an LU can be a stack of products.""",
        example = "[{...}]"
    )
    @field:Valid
    val loadUnit: List<Product>,
    @field:Schema(
        description = """Who cause the load unit to move, can be system if that was an automatic action.""",
        example = "per.person@nb.no"
    )
    @field:NotBlank
    val user: String,
    @field:Schema(
        description = """Warehouse in which the TU moved.""",
        example = "Sikringsmagasin_2"
    )
    @field:NotBlank
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
    @field:Schema(
        description = """Marks the product as confidential, meaning only people with special access can view, modify, or request the product.""",
        example = "false"
    )
    val confidentialProduct: Boolean,
    @field:Schema(
        description = """Name of the host system which the product belongs to.""",
        example = "AXIELL"
    )
    @field:NotBlank
    val hostName: String,
    @field:Schema(
        description = """Product ID from the host system, usually a barcode value or an equivalent ID.""",
        example = "mlt-12345"
    )
    @field:NotBlank
    val productId: String,
    @field:Schema(
        description = """Product's owner, usually the National Library of Norway (NB) or the National Archives of Norway (AV).""",
        example = "NB"
    )
    @field:NotBlank
    val productOwner: String,
    @field:Schema(
        description = """Product version ID in the storage system, seems to always have value "Default".""",
        example = "Default"
    )
    @field:NotBlank
    val productVersionId: String,
    @field:Schema(
        description = """Product quantity in the TU, SynQ uses doubles for quantity, however we convert it to integers.""",
        example = "1.0"
    )
    @field:PositiveOrZero(message = "Quantity on hand must not be negative. It must be zero or higher")
    val quantityOnHand: Int?,
    @field:Schema(
        description = """The amount of product which was moved between TUs. SynQ uses doubles for quantity, however we convert it to integers.""",
        example = "1.0"
    )
    @field:Positive(message = "Quantity moved must not be negative. It must be zero or higher")
    val quantityMove: Int?,
    @field:Schema(
        description = """Signifies the product is missing, damaged, or otherwise suspect, and it requires manual action from the operator.""",
        example = "false"
    )
    val suspect: Boolean,
    @field:Schema(
        description = """List of attributes for the product.""",
        example = "[{...}]"
    )
    @field:Valid
    val attributeValue: List<AttributeValue>,
    @field:Schema(
        description = """Position of the product in the TU, not used by us so this is pretty irrelevant.""",
        example = "{...}"
    )
    val position: Position
) {
    fun toMoveItemPayload(
        prevLocation: String,
        location: String
    ): MoveItemPayload {
        var quantity = quantityMove ?: throw ItemMovingException("Quantity moved must not be null")
        if (prevLocation == "AutoStore_Warehouse") {
            quantity = quantity.unaryMinus()
        }

        return MoveItemPayload(
            hostName = getHostNameFromSynqTypes(),
            hostId = productId,
            quantity = quantity,
            location = location
        )
    }

    fun toUpdateItemPayload(location: String): UpdateItemPayload =
        UpdateItemPayload(
            hostName = getHostNameFromSynqTypes(),
            hostId = productId,
            quantity = quantityOnHand ?: throw ItemMovingException("Quantity on hand must not be null"),
            location = location
        )

    private fun getHostNameFromSynqTypes(): HostName {
        if (hostName.lowercase() == "mavis") {
            // As of now over 160k items exist with hostName Mavis in SynQ.
            // These need to be migrated to Axiell, but before that is done we can cheat the system by converting Mavis -> Axiell
            return HostName.AXIELL
        }

        return HostName.fromString(hostName)
    }
}

@Schema(
    description = """Represents a product's attribute.""",
    example = """
    {
      "name" : "materialStatus",
      "value" : "Available"
    }"""
)
data class AttributeValue(
    @field:Schema(
        description = """Name of the attribute.""",
        example = "materialStatus"
    )
    @field:NotBlank
    val name: String,
    @field:Schema(
        description = """Value of the attribute.""",
        example = "Available"
    )
    @field:NotBlank
    val value: String
)

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
    @field:Schema(
        description = """X position of the product in the TU.""",
        example = "1"
    )
    val xPosition: Int,
    @field:Schema(
        description = """Y position of the product in the TU.""",
        example = "1"
    )
    val yPosition: Int,
    @field:Schema(
        description = """Z position of the product in the TU.""",
        example = "1"
    )
    val zPosition: Int
)
