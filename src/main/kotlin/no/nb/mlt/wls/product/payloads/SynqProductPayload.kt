package no.nb.mlt.wls.product.payloads

data class SynqProductPayload(
    val productId: String,
    val owner: Owner,
    val barcode: Barcode,
    val productCategory: String,
    val productUom: ProductUom,
    val confidential: Boolean,
    val hostName: String
) {
    enum class Packaging {
        OBJ,
        ESK
    }

    enum class Owner {
        NB
    }

    class Barcode(val barcodeId: String)

    class ProductUom(val uomId: Packaging)
}
