package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Payload for confirming picking of the order products/items in SynQ.",
    example = """
    {
      "orderLine" : [
        {
          "confidentialProduct" : false,
          "hostName" : "Axiell",
          "orderLineNumber" : 1,
          "orderTuId" : "SYS_TU_00000001157",
          "orderTuType" : "UFO",
          "productId" : "mlt-12345",
          "productVersionId" : "Default",
          "quantity" : 1.0,
          "attributeValue" : [
            {
              "name" : "materialStatus",
              "value" : "Available"
            }
          ]
        }
      ],
      "operator" : "per.person@nb.no",
      "warehouse" : "Sikringsmagasin_2"
    }"""
)
data class SynqOrderPickingConfirmationPayload(
    @Schema(
        description = "List of order lines representing the picked products/items.",
        example = "[{...}]"
    )
    val orderLine: List<OrderLine>,
    @Schema(
        description = "User who confirmed the picking.",
        example = "per@person@nb.no"
    )
    val operator: String,
    @Schema(
        description = "Name of the warehouse where the order products/items are located.",
        example = "Sikringsmagasin_2"
    )
    val warehouse: String
)

@Schema(
    description = "Order line representing a picked product/item in an order.",
    example = """
    {
      "confidentialProduct" : false,
      "hostName" : "Axiell",
      "orderLineNumber" : 1,
      "orderTuId" : "SYS_TU_00000001157",
      "orderTuType" : "UFO",
      "productId" : "mlt-12345",
      "productVersionId" : "Default",
      "quantity" : 1.0,
      "attributeValue" : [
        {
          "name" : "materialStatus",
          "value" : "Available"
        }
      ]
    }"""
)
data class OrderLine(
    @Schema(
        description = "Whether the product/item is confidential.",
        example = "false"
    )
    val confidentialProduct: Boolean,
    @Schema(
        description = "Name of the host system which placed the order/owns the order products/items.",
        example = "Axiell"
    )
    val hostName: String,
    @Schema(
        description = "Order line number.",
        example = "1"
    )
    val orderLineNumber: Int,
    @Schema(
        description = "ID of the transport unit (TU) with the product/item.",
        example = "SYS_TU_00000001157"
    )
    val orderTuId: String,
    @Schema(
        description = "Type of the transport unit (TU) with the product/item.",
        example = "UFO"
    )
    val orderTuType: String,
    @Schema(
        description = "Storage ID of the product.",
        example = "mlt-12345"
    )
    val productId: String,
    @Schema(
        description = "Version ID of the product.",
        example = "Default"
    )
    val productVersionId: String,
    @Schema(
        description = "Quantity of the product/item.",
        example = "1.0"
    )
    val quantity: Double,
    @Schema(
        description = "List of attribute values of the product/item.",
        example = "[{...}]"
    )
    val attributeValue: List<AttributeValue>
)
