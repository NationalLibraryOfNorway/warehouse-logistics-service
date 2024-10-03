package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Payload for receiving Item updates in batch from SynQ storage system.",
    example = """
    {
      "tuId" : "6942066642",
      "location" : "SYNQ_WAREHOUSE",
      "prevLocation" : "WS_PLUKKSENTER_1",
      "loadUnit" : [
        {
          "confidentialProduct" : false,
          "hostName" : "Axiell",
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
data class SynqBatchItemUpdatePayload(
    @Schema(
        description = "ID of the transport unit in the SynQ storage system.",
        example = "6942066642"
    )
    val tuId: String,
    @Schema(
        description = "Current location of the transport unit and its contents in the SynQ storage system.",
        example = "SYNQ_WAREHOUSE"
    )
    val location: String,
    @Schema(
        description = "Previous location of the transport unit and its contents in the SynQ storage system.",
        example = "WS_PLUKKSENTER_1"
    )
    val prevLocation: String,
    @Schema(
        description = "List of products in the transport unit.",
        example = "[{...}]"
    )
    val loadUnit: List<Product>,
    @Schema(
        description = "User who initiated the update.",
        example = "per.person@nb.no"
    )
    val user: String,
    @Schema(
        description = "Warehouse where the transport unit is located.",
        example = "Sikringsmagasin_2"
    )
    val warehouse: String
)

@Schema(
    description = "Product information class, this is equivalent to an Item in the Hermes WLS.",
    example = """
    {
      "confidentialProduct" : false,
      "hostName" : "Axiell",
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
        description = "Marks the product as confidential, meaning only people with special access can modify or view the product.",
        example = "false"
    )
    val confidentialProduct: Boolean,
    @Schema(
        description = "Name of the host system where the product is registered.",
        example = "Axiell"
    )
    val hostName: String,
    @Schema(
        description = "Product ID from the host system, usually a barcode or an equivalent ID.",
        example = "mlt-12345"
    )
    val productId: String,
    @Schema(
        description = "Owner of the product, usually the National Library of Norway (NB) or the National Archives of Norway (AV).",
        example = "NB"
    )
    val productOwner: String,
    @Schema(
        description = "Product version ID in the storage system, seems to always have value 'Default'.",
        example = "Default"
    )
    val productVersionId: String,
    @Schema(
        description = "Quantity of the product in the transport unit, uses float, however in our case we operate only in whole numbers.",
        example = "1.0"
    )
    val quantityOnHand: Double,
    @Schema(
        description = "Signifies the product is marked as suspect in the storage system and needs to be manually verified.",
        example = "false"
    )
    val suspect: Boolean,
    @Schema(
        description = "List of attribute values for the product.",
        example = "[{...}]"
    )
    val attributeValues: List<AttributeValue>,
    @Schema(
        description = "Position of the product in the TU, not used by us so it always have default values of '1,1,1'.",
        example = "{...}"
    )
    val position: Position
)

@Schema(
    description = "Represents an attribute value for the product.",
    example = """
    {
      "name" : "materialStatus",
      "value" : "Available"
    }"""
)
data class AttributeValue(
    @Schema(
        description = "Name of the attribute.",
        example = "materialStatus"
    )
    val name: String,
    @Schema(
        description = "Value of the attribute.",
        example = "Available"
    )
    val value: String
)

@Schema(
    description = "Represents position of the product in the TU.",
    example = """
    {
      "xPosition" : 1,
      "yPosition" : 1,
      "zPosition" : 1
    }"""
)
data class Position(
    @Schema(
        description = "X position of the product in the TU.",
        example = "1"
    )
    val xPosition: Int,
    @Schema(
        description = "Y position of the product in the TU.",
        example = "1"
    )
    val yPosition: Int,
    @Schema(
        description = "Z position of the product in the TU.",
        example = "1"
    )
    val zPosition: Int
)
