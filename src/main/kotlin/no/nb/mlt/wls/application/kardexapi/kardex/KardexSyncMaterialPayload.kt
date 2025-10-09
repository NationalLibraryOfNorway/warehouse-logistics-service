package no.nb.mlt.wls.application.kardexapi.kardex

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems

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
    val quantity: Double,
    @field:Schema(
        description = """Name of the warehouse where the materials is located."""
    )
    val location: String
)

// TODO - Is this metadata good enough?
fun List<KardexSyncMaterialPayload>.toSyncPayloads(): List<SynchronizeItems.ItemToSynchronize> =
    this.map { kardexPayload ->
        SynchronizeItems.ItemToSynchronize(
            hostId = kardexPayload.hostId,
            hostName = HostName.fromString(kardexPayload.hostName),
            quantity = kardexPayload.quantity.toInt(),
            location = kardexPayload.location,
            associatedStorage = AssociatedStorage.KARDEX,
            description = "",
            itemCategory = ItemCategory.UNKNOWN,
            packaging = Packaging.UNKNOWN,
            currentPreferredEnvironment = Environment.NONE
        )
    }
