package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.ValidationException

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
) {
    /**
     * Validates the packet data and structure
     */
    fun validate() {
        if (orderLine.isEmpty()) {
            throw ValidationException("Picking update does not contain any elements in the order line")
        }

        if (operator.isBlank()) {
            throw ValidationException("Picking update's operator can not be blank")
        }

        if (warehouse.isBlank()) {
            throw ValidationException("Picking update's warehouse can not be blank")
        }
        // Validates the hostname present on the order lines
        getValidHostName()

        orderLine.forEach(OrderLine::validate)
    }

    @Throws(ValidationException::class)
    fun getValidHostName(): HostName {
        val hostName = getHostNameString() ?: throw ValidationException("Unable to get hostname from order lines")
        try {
            return HostName.valueOf(hostName)
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Hostname $hostName is not recognized by WLS", e)
        }
    }

    private fun getHostNameString(): String? {
        return this.orderLine.firstOrNull()?.hostName
    }

    /**
     * Creates a map between the id and the quantity picked from each product in the order line
     */
    fun mapProductsToQuantity(): Map<String, Int> {
        val map: MutableMap<String, Int> = mutableMapOf()
        this.orderLine.map { orderLine ->
            map.put(
                orderLine.productId,
                orderLine.quantity
            )
        }
        return map
    }
}

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
        description = "Storage ID of the product/item.",
        example = "mlt-12345"
    )
    val productId: String,
    @Schema(
        description = "Version ID of the product/item.",
        example = "Default"
    )
    val productVersionId: String,
    @Schema(
        description = "Quantity of the product/item.",
        example = "1.0"
    )
    val quantity: Int,
    @Schema(
        description = "List of attribute values of the product/item.",
        example = "[{...}]"
    )
    val attributeValue: List<AttributeValue>
) {
    fun validate() {
        if (hostName.isBlank()) {
            throw ValidationException("Order Line's host name can not be blank")
        }

        if (HostName.entries.toTypedArray().none { it.name.uppercase() == hostName.uppercase() }) {
            throw ValidationException("Order Line's host name: '$hostName' is not valid")
        }

        if (orderLineNumber < 0) {
            throw ValidationException("Order Line's line number must be positive")
        }

        if (orderTuId.isBlank()) {
            throw ValidationException("Order Line's TU ID can not be blank")
        }

        if (orderTuType.isBlank()) {
            throw ValidationException("Order Line's TU type can not be blank")
        }

        if (productId.isBlank()) {
            throw ValidationException("Order Line's product ID can not be blank")
        }

        if (productVersionId.isBlank()) {
            throw ValidationException("Order Line's product version ID can not be blank")
        }

        if (quantity < 0) {
            throw ValidationException("Order Line's quantity for the product $productId must be positive")
        }

        if (attributeValue.isNotEmpty()) {
            attributeValue.forEach(AttributeValue::validate)
        }
    }
}
