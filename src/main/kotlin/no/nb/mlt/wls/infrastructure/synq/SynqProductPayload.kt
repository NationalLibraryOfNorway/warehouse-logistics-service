package no.nb.mlt.wls.infrastructure.synq

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
        ESK
    }

    data class Barcode(val barcodeId: String)

    data class ProductUom(val uomId: SynqPackaging)
}

fun Item.toSynqPayload() =
    SynqProductPayload(
        productId = hostId,
        owner = owner.toSynqOwner(),
        barcode = SynqProductPayload.Barcode(hostId),
        description = description,
        productCategory = toSynqCategory(itemCategory),
        productUom = SynqProductPayload.ProductUom(packaging.toSynqPackaging()),
        confidential = false,
        hostName = hostName.toString()
    )

fun toSynqCategory(category: ItemCategory): String {
    return when (category) {
        ItemCategory.PAPER -> "papir"
        ItemCategory.DISC -> "plater"
        ItemCategory.FILM -> "film"
        ItemCategory.PHOTO -> "foto"
        ItemCategory.EQUIPMENT -> "gjenstand"
        ItemCategory.BULK_ITEMS -> "sekkepost"
        ItemCategory.MAGNETIC_TAPE -> "magnetbånd"
    }
}
