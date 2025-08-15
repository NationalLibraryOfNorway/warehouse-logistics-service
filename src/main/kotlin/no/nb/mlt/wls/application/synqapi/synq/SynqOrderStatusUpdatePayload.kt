package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.IllegalOrderStateException

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
    @field:Schema(
        description = """Previous order status.""",
        example = "PICKED"
    )
    val prevStatus: SynqOrderStatus,
    @field:Schema(
        description = """Current order status.""",
        example = "COMPLETED"
    )
    val status: SynqOrderStatus,
    @field:Schema(
        description = """Name of the host system which placed the order.""",
        example = "AXIELL"
    )
    val hostName: String,
    @field:Schema(
        description = """Name of the warehouse where the order products/items are located.""",
        example = "Sikringmagasin_2"
    )
    @field:NotBlank(message = "Order status update cannot have blank warehouse")
    val warehouse: String
)

val SynqOrderStatusUpdatePayload.sanitizedHostName: String
    get() {
        if (hostName.lowercase() == "mavis") {
            return "AXIELL"
        }

        return hostName
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
        SynqOrderStatus.COMPLETED -> throw IllegalOrderStateException("SynQ is not allowed to decide if the order is complete")
        SynqOrderStatus.CANCELLED -> Order.Status.DELETED
        else -> Order.Status.IN_PROGRESS
    }
