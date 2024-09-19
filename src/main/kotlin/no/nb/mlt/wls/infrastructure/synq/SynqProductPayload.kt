package no.nb.mlt.wls.infrastructure.synq

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
