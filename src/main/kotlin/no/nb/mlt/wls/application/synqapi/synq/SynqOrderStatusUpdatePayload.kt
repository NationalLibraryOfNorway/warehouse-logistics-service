package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import no.nb.mlt.wls.domain.model.Order

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
    @field:NotBlank(message = "Order status update cannot have blank warehouse")
    val warehouse: String
)

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
