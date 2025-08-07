package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PositiveOrZero
import no.nb.mlt.wls.application.validations.ValidHostName
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
    @field:Schema(
        description = """List of order lines representing the picked products/items.""",
        example = "[{...}]"
    )
    @field:Valid
    @field:NotEmpty(message = "Picking update does not contain any elements in the order line")
    val orderLine: List<OrderLine>,
    @field:Schema(
        description = """Who picked the products/items.""",
        example = "per@person@nb.no"
    )
    @field:NotBlank(message = "Picking update's operator can not be blank")
    val operator: String,
    @field:Schema(
        description = """Name of the warehouse where the order products/items were picked from.""",
        example = "Sikringsmagasin_2"
    )
    @field:NotBlank(message = "Picking update's warehouse can not be blank")
    val warehouse: String
) {
    @Throws(ValidationException::class)
    fun getValidHostName(): HostName {
        val hostName = getHostNameString()
        try {
            if (hostName.lowercase() == "mavis") {
                // As of now over 160k items exist with hostName Mavis in SynQ.
                // These need to be migrated to Axiell, but before that is done we can cheat the system by converting Mavis -> Axiell
                return HostName.AXIELL
            }

            return HostName.fromString(hostName)
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Hostname $hostName is not recognized by WLS", e)
        }
    }

    private fun getHostNameString(): String =
        this.orderLine.firstOrNull()?.hostName ?: throw ValidationException("Unable to get hostname from order lines")

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
    @field:Schema(
        description = """Marks the product as confidential, meaning only people with special access can view, modify, or request the product.""",
        example = "false"
    )
    val confidentialProduct: Boolean,
    @field:Schema(
        description = """Name of the host system which the product belongs to.""",
        example = "AXIELL"
    )
    @field:ValidHostName
    @field:NotBlank(message = "Order Line's host name can not be blank")
    val hostName: String,
    @field:Schema(
        description = """Order line number/index.""",
        example = "1"
    )
    @field:Min(value = 1, message = "Order Line's line number must be positive")
    val orderLineNumber: Int,
    @field:Schema(
        description = """ID of the transport unit (TU) with the product/item in SynQ.""",
        example = "SYS_TU_00000001157"
    )
    @field:NotBlank(message = "Order Line's TU ID can not be blank")
    val orderTuId: String,
    @field:Schema(
        description = """Type of the transport unit (TU) with the product/item.""",
        example = "UFO"
    )
    @field:NotBlank(message = "Order Line's TU type can not be blank")
    val orderTuType: String,
    @field:Schema(
        description = """Product ID from the host system, usually a barcode value or an equivalent ID.""",
        example = "mlt-12345"
    )
    @field:NotBlank(message = "Order Line's product ID can not be blank")
    val productId: String,
    @field:Schema(
        description = """Product version ID in the storage system, seems to always have value "Default".""",
        example = "Default"
    )
    @field:NotBlank(message = "Order Line's product version ID can not be blank")
    val productVersionId: String,
    @field:Schema(
        description = """Number of picked products/items, in our case it should be 1 and nothing more.""",
        example = "1.0"
    )
    @field:PositiveOrZero
    val quantity: Int,
    @field:Schema(
        description = """List of attributes for the product.""",
        example = "[{...}]"
    )
    @field:Valid
    val attributeValue: List<AttributeValue>
)
