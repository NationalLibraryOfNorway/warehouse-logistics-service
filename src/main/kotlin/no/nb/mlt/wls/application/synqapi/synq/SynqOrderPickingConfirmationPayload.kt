package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.ValidationException

@Schema(
    description = """Payload which confirms the picking of products/items in a SynQ order.""",
    example = """
    {
      "orderLine" : [
        {
          "confidentialProduct" : false,
          "hostName" : "AXIELL",
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
        description = """List of order lines representing the picked products/items.""",
        example = "[{...}]"
    )
    val orderLine: List<OrderLine>,
    @Schema(
        description = """Who picked the products/items.""",
        example = "per@person@nb.no"
    )
    val operator: String,
    @Schema(
        description = """Name of the warehouse where the order products/items were picked from.""",
        example = "Sikringsmagasin_2"
    )
    val warehouse: String
) {
    @Throws(ValidationException::class)
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
        // Validates the hostname based on the value of hostname in order lines
        getValidHostName()

        orderLine.forEach(OrderLine::validate)
    }

    @Throws(ValidationException::class)
    fun getValidHostName(): HostName {
        val hostName = getHostNameString()
        try {
            return HostName.fromString(hostName)
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Hostname $hostName is not recognized by WLS", e)
        }
    }

    private fun getHostNameString(): String {
        return this.orderLine.firstOrNull()?.hostName ?: throw ValidationException("Unable to get hostname from order lines")
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
    description = """Order line representing a picked product/item in a SynQ order.""",
    example = """
    {
      "confidentialProduct" : false,
      "hostName" : "AXIELL",
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
        description = """Marks the product as confidential, meaning only people with special access can view, modify, or request the product.""",
        example = "false"
    )
    val confidentialProduct: Boolean,
    @Schema(
        description = """Name of the host system which the product belongs to.""",
        example = "AXIELL"
    )
    val hostName: String,
    @Schema(
        description = """Order line number/index.""",
        example = "1"
    )
    val orderLineNumber: Int,
    @Schema(
        description = """ID of the transport unit (TU) with the product/item in SynQ.""",
        example = "SYS_TU_00000001157"
    )
    val orderTuId: String,
    @Schema(
        description = """Type of the transport unit (TU) with the product/item.""",
        example = "UFO"
    )
    val orderTuType: String,
    @Schema(
        description = """Product ID from the host system, usually a barcode value or an equivalent ID.""",
        example = "mlt-12345"
    )
    val productId: String,
    @Schema(
        description = """Product version ID in the storage system, seems to always have value "Default".""",
        example = "Default"
    )
    val productVersionId: String,
    @Schema(
        description = """Number of picked products/items, in our case it should be 1 and nothing more.""",
        example = "1.0"
    )
    val quantity: Int,
    @Schema(
        description = """List of attributes for the product.""",
        example = "[{...}]"
    )
    val attributeValue: List<AttributeValue>
) {
    @Throws(ValidationException::class)
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
            throw ValidationException("Order Line's quantity for the product '$productId' must be positive")
        }

        if (attributeValue.isNotEmpty()) {
            attributeValue.forEach(AttributeValue::validate)
        }
    }
}
