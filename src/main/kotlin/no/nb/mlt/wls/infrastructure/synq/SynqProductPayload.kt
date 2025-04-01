package no.nb.mlt.wls.infrastructure.synq

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException

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
        productCategory = toSynqCategory(itemCategory),
        productUom = SynqProductPayload.ProductUom(packaging.toSynqPackaging()),
        confidential = false,
        hostName = hostName.toString()
    )

fun toSynqCategory(category: ItemCategory): String =
    when (category) {
        ItemCategory.PAPER -> "papir"
        ItemCategory.DISC -> "plate"
        ItemCategory.FILM -> "film"
        ItemCategory.EQUIPMENT -> "gjenstand"
        ItemCategory.BULK_ITEMS -> "sekkepost"
        ItemCategory.MAGNETIC_TAPE -> "magnetbånd"
        ItemCategory.PHOTO -> "fotografi"
        ItemCategory.FRAGILE -> throw StorageSystemException("$category items cannot go into SynQ")
    }
