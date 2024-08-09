package no.nb.mlt.wls.product.payloads

import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.core.data.synq.SynqOwner
import no.nb.mlt.wls.core.data.synq.toOwner
import no.nb.mlt.wls.core.data.synq.toSynqOwner
import no.nb.mlt.wls.product.model.Product
import no.nb.mlt.wls.product.payloads.SynqProductPayload.SynqPackaging
import no.nb.mlt.wls.product.payloads.SynqProductPayload.SynqPackaging.ESK
import no.nb.mlt.wls.product.payloads.SynqProductPayload.SynqPackaging.OBJ

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

// unused
fun SynqProductPayload.toProduct() =
    Product(
        hostName = HostName.valueOf(hostName),
        hostId = barcode.barcodeId,
        productCategory = productCategory,
        description = description,
        packaging = productUom.uomId.toPackaging(),
        location = "SYNQ",
        quantity = 1.0,
        preferredEnvironment = Environment.NONE,
        owner = owner.toOwner()
    )

// FIXME - metadata for barcode is lost
fun Product.toSynqPayload() =
    SynqProductPayload(
        productId = hostId,
        owner = owner.toSynqOwner(),
        barcode = SynqProductPayload.Barcode(hostId),
        description = description,
        productCategory = productCategory,
        productUom = SynqProductPayload.ProductUom(packaging.toSynqPackaging()),
        confidential = false,
        hostName = hostName.toString()
    )

fun SynqPackaging.toPackaging(): Packaging =
    when (this) {
        OBJ -> Packaging.NONE
        ESK -> Packaging.BOX
    }

fun Packaging.toSynqPackaging(): SynqPackaging =
    when (this) {
        Packaging.NONE -> OBJ
        Packaging.BOX -> ESK
    }
