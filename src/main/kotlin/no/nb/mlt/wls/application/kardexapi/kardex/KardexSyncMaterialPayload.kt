package no.nb.mlt.wls.application.kardexapi.kardex

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems

data class KardexSyncMaterialPayload(
    @Schema(
        description = """The main material ID of the item."""
    )
    val materialName: String,
    @Schema(
        description = """Name of the host system which the material belongs to.""",
        example = "AXIELL"
    )
    val hostName: String,
    @Schema(
        description = """The first info field from Kardex, which usually describes the item."""
    )
    val description: String,
    @Schema(
        description = """The current quantity of the item."""
    )
    val quantity: Int,
    @Schema(
        description = """The item category for this material."""
    )
    val itemCategory: String,
    @Schema(
        description = """Whether the item is a single object or a container with other items inside.
            "NONE" is for single objects, "ABOX" is for archival boxes, etc.
            NOTE: It is up to the catalogue to keep track of the items inside a container.""",
        examples = ["NONE", "BOX", "ABOX"]
    )
    val packaging: String?,
    @Schema(
        description = """What kind of environment the item should be stored in.
            "NONE" means item has no preference for storage environment,
            "FREEZE" means that item should be stored frozen when stored, etc.
            NOTE: This is not a guarantee that the item will be stored in the preferred environment.
            In cases where storage space is limited, the item may be stored in regular environment.""",
        examples = ["NONE", "FREEZE"]
    )
    val environment: Environment?,
    @Schema(
        description = """Name of the warehouse where the order materials/items are located."""
    )
    val warehouse: String
) {
    fun mapToSyncItems(): List<SynchronizeItems.ItemToSynchronize> {
        return listOf(
            SynchronizeItems.ItemToSynchronize(
                hostId = materialName,
                hostName = HostName.fromString(hostName),
                description = description,
                location = warehouse,
                quantity = quantity,
                itemCategory = ItemCategory.fromString(itemCategory),
                packaging = if (packaging == null) Packaging.NONE else Packaging.valueOf(packaging.uppercase()),
                currentPreferredEnvironment = environment ?: Environment.NONE
            )
        )
    }
}
