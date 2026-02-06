package no.nb.mlt.wls.application.kardexapi.kardex

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ValidationException

private val logger = KotlinLogging.logger {}

data class KardexSyncMaterialPayload(
    @field:Schema(
        description = """The main material ID of the item."""
    )
    val hostId: String,
    @field:Schema(
        description = """Name of the host system which the material belongs to.""",
        example = "AXIELL"
    )
    val hostName: String,
    @field:Schema(
        description = """The current quantity of the material."""
    )
    val quantity: String,
    @field:Schema(
        description = """Name of the warehouse where the materials is located."""
    )
    val location: String,
    @field:Schema(
        description = """Description of the material."""
    )
    val description: String
)

fun List<KardexSyncMaterialPayload>.toSyncPayloads(): List<SynchronizeItems.ItemToSynchronize> =
    this.map { kardexPayload -> kardexPayload.toSyncPayload() }

fun KardexSyncMaterialPayload.toSyncPayload(): SynchronizeItems.ItemToSynchronize {
    // Handle quantity, which is a double in spec, but is sent to us as string
    val amount =
        if (quantity.isBlank()) {
            0
        } else {
            // Check for decimal points, which for now is invalid
            if (quantity.toDouble().rem(1) == 0.0) {
                quantity.toDouble()
            } else {
                logger.error {
                    "Tried to sync item with quantity between 0.0 and 1.0: $this"
                }
                throw ValidationException("Unable to synchronize item. Quantity must be a whole number")
            }
        }
    // REVIEW - Should we also allow items without HostName, or should these be corrected manually?
    val hostName =
        try {
            HostName.fromString(this.hostName)
        } catch (_: IllegalArgumentException) {
            logger.warn {
                "Received invalid hostname from Kardex Item ${this.hostId}: '${this.hostName.ifBlank { "<blank>" }}'"
            }
            HostName.UNKNOWN
        }
    return SynchronizeItems.ItemToSynchronize(
        hostId = this.hostId,
        hostName = hostName,
        quantity = amount.toInt(),
        location = this.location.ifBlank { UNKNOWN_LOCATION },
        associatedStorage = AssociatedStorage.KARDEX,
        description = this.description,
        itemCategory = ItemCategory.UNKNOWN,
        packaging = Packaging.UNKNOWN,
        currentPreferredEnvironment = Environment.NONE,
        confidential = false
    )
}
