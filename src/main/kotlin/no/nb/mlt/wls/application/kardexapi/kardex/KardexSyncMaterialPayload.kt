package no.nb.mlt.wls.application.kardexapi.kardex

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ValidationException

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
    @field:Pattern(
        regexp = "^(\\d+(\\.0)?)?$",
        message = "Quantity must be blank or a positive whole number (e.g., '', '1.0', '42' are valid)"
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

@Throws(ValidationException::class)
fun KardexSyncMaterialPayload.toSyncPayload(): SynchronizeItems.ItemToSynchronize {
    // Handle quantity, which is a double in spec, but is sent to us as string

    val newAmount = quantity.ifBlank { "0.0" }.toDouble().toInt()
    try {
        val validatedHostName = HostName.fromString(this.hostName)
        return SynchronizeItems.ItemToSynchronize(
            hostId = this.hostId,
            hostName = validatedHostName,
            quantity = newAmount,
            location = this.location.ifBlank { UNKNOWN_LOCATION },
            associatedStorage = AssociatedStorage.KARDEX,
            description = this.description,
            itemCategory = ItemCategory.UNKNOWN,
            packaging = Packaging.UNKNOWN,
            currentPreferredEnvironment = Environment.NONE,
            confidential = false
        )
    } catch (_: IllegalArgumentException) {
        throw ValidationException("Unable to sync item $hostId with hostname=${hostName.ifBlank { "<empty>" }}")
    }
}
