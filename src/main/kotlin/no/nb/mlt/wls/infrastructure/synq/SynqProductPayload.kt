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
        productCategory = toSynqCategory(itemCategory).toString(),
        productUom = SynqProductPayload.ProductUom(packaging.toSynqPackaging()),
        confidential = false,
        hostName = hostName.toString()
    )

fun toSynqCategory(itemCategory: ItemCategory): ItemCategory {
    return when (itemCategory) {
        ItemCategory.lydbånd -> ItemCategory.magnetbånd
        ItemCategory.videobånd -> ItemCategory.magnetbånd
        ItemCategory.safetyfilm -> ItemCategory.film
        ItemCategory.nitratfilm -> ItemCategory.film
        else -> itemCategory
    }
}
