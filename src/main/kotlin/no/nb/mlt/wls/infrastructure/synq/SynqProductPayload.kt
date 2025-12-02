package no.nb.mlt.wls.infrastructure.synq

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory

data class SynqProductPayload(
    val productId: String,
    val owner: SynqOwner,
    val barcode: Barcode,
    val description: String,
    val productCategory: String,
    val productUom: ProductUom,
    val confidential: Boolean,
    val hostName: String
) {
    enum class SynqPackaging {
        OBJ,
        ESK,
        ABOX
    }

    data class Barcode(
        val barcodeId: String
    )

    data class ProductUom(
        val uomId: SynqPackaging
    )
}

fun Item.toSynqPayload() =
    SynqProductPayload(
        productId = hostId,
        owner = toSynqOwner(hostName),
        barcode = SynqProductPayload.Barcode(hostId),
        description = description,
        productCategory = toSynqCategory(toSynqOwner(hostName), itemCategory),
        productUom = SynqProductPayload.ProductUom(packaging.toSynqPackaging()),
        confidential = false,
        hostName = toSynqHostname(hostName)
    )

fun toSynqCategory(
    owner: SynqOwner,
    category: ItemCategory
): String {
    if (owner == SynqOwner.AV) return "Arkivmateriale"

    return when (category) {
        ItemCategory.FILM -> "Film"

        ItemCategory.PHOTO -> "Fotografi"

        ItemCategory.MAGNETIC_TAPE -> "Magnetbånd"

        // Need to double-check with Espen if we actually need/want this
        ItemCategory.PAPER -> "Papir"

        ItemCategory.DISC -> "Plate"

        // Need to double-check with Espen if we actually need/want this
        ItemCategory.BULK_ITEMS -> "Sekkepost"

        ItemCategory.UNKNOWN -> throw IllegalArgumentException("Unknown item category")

        else -> throw IllegalArgumentException("Illegal item category for SynQ: $category")
    }
}

fun toSynqHostname(hostName: HostName): String =
    when (hostName) {
        HostName.ALMA -> "Alma"

        HostName.ASTA -> "Asta"

        HostName.MAVIS -> "Axiell"

        HostName.AXIELL -> "Axiell"

        // This needs to be changed when we have fixed SynQ to use Axiell instead of Mavis
        HostName.TEMP_STORAGE -> "mellomlager"

        HostName.UNKNOWN -> throw NotImplementedError("Creating Products for HostName.UNKNOWN is not supported")
    }
