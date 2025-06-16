package no.nb.mlt.wls.infrastructure.synq

import no.nb.mlt.wls.domain.model.Environment
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
        productCategory = toSynqCategory(toSynqOwner(hostName), itemCategory, preferredEnvironment),
        productUom = SynqProductPayload.ProductUom(packaging.toSynqPackaging()),
        confidential = false,
        hostName = toSynqHostname(hostName)
    )

fun toSynqCategory(
    owner: SynqOwner,
    category: ItemCategory,
    environment: Environment
): String {
    if (owner == SynqOwner.AV) return "Arkivmateriale"

    if (environment == Environment.FREEZE) {
        if (category == ItemCategory.FILM) return "Film_Frys"
        if (category == ItemCategory.PHOTO) return "Fotografi_Frys"
    }
    return when (category) {
        ItemCategory.FILM -> "Film"
        ItemCategory.PHOTO -> "Fotografi"
        ItemCategory.EQUIPMENT -> "Gjenstand"
        ItemCategory.MAGNETIC_TAPE -> "Magnetbånd"
        ItemCategory.PAPER -> "Papir"
        ItemCategory.DISC -> "Plate"
        ItemCategory.BULK_ITEMS -> "Sekkepost"
        ItemCategory.UNKNOWN -> throw IllegalArgumentException("Unknown item category")
    }
}

fun toSynqHostname(hostName: HostName): String =
    when (hostName) {
        HostName.ALMA -> "Alma"
        HostName.ASTA -> "Asta"
        HostName.MAVIS -> "Mavis"
        HostName.AXIELL -> "Axiell"
        HostName.TEMP_STORAGE -> "mellomlager"
        HostName.NONE -> throw NotImplementedError("Creating Products for HostName.NONE is not supported")
    }
