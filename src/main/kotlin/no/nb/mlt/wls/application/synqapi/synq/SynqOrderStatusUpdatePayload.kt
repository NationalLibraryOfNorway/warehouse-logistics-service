package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.ValidationException

@Schema(
    description = """Payload with updated status information for an order placed in SynQ.""",
    example = """
    {
      "prevStatus": "PICKED",
      "status": "COMPLETED",
      "hostName" : "AXIELL",
      "warehouse" : "Sikringmagasin_2"
    }"""
)
data class SynqOrderStatusUpdatePayload(
    @Schema(
        description = """Previous order status.""",
        example = "PICKED"
    )
    val prevStatus: SynqOrderStatus,
    @Schema(
        description = """Current order status.""",
        example = "COMPLETED"
    )
    val status: SynqOrderStatus,
    @Schema(
        description = """Name of the host system which placed the order.""",
        example = "AXIELL"
    )
    val hostName: String,
    @Schema(
        description = """Name of the warehouse where the order products/items are located.""",
        example = "Sikringmagasin_2"
    )
    val warehouse: String
) {
    @Throws(ValidationException::class)
    fun validate() {
        if (warehouse.isBlank()) {
            throw ValidationException("Order status update cannot hve a blank warehouse")
        }
    }
}

enum class SynqOrderStatus {
    ALLOCATED,
    ALLOCATING,
    CANCELLED,
    COMPLETED,
    CONSOLIDATED,
    LOADED,
    NEW,
    PARTIALLY_ALLOCATED,
    PICKED,
    PICKING,
    RELEASED_FOR_ALLOCATION,
    RELEASED,
    STAGED
}

fun SynqOrderStatusUpdatePayload.getConvertedStatus() =
    when (status) {
        SynqOrderStatus.NEW -> Order.Status.NOT_STARTED
        SynqOrderStatus.COMPLETED -> Order.Status.COMPLETED
        SynqOrderStatus.CANCELLED -> Order.Status.DELETED
        else -> Order.Status.IN_PROGRESS
    }
