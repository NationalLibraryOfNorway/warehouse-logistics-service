package no.nb.mlt.wls.product.payloads

import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.model.Product
import no.nb.mlt.wls.product.payloads.SynqProductPayload.SynqOwner
import no.nb.mlt.wls.product.payloads.SynqProductPayload.SynqOwner.NB
import no.nb.mlt.wls.product.payloads.SynqProductPayload.SynqPackaging
import no.nb.mlt.wls.product.payloads.SynqProductPayload.SynqPackaging.ESK
import no.nb.mlt.wls.product.payloads.SynqProductPayload.SynqPackaging.OBJ

data class SynqProductPayload(
    val productId: String,
    val owner: SynqOwner,
    val barcode: Barcode,
    val productCategory: String,
    val productUom: ProductUom,
    val confidential: Boolean,
    val hostName: String
) {
    enum class SynqPackaging {
        OBJ,
        ESK
    }

    enum class SynqOwner {
        NB
    }

    class Barcode(val barcodeId: String)

    class ProductUom(val uomId: SynqPackaging)
}

// TODO - Will we ever receive this payload?
fun SynqProductPayload.toProduct() =
    Product(
        hostName = HostName.valueOf(hostName),
        hostId = barcode.barcodeId,
        category = productCategory,
        description = "",
        packaging = productUom.uomId.toPackaging(),
        location = "SYNQ",
        quantity = 1.0f,
        environment = Environment.NONE,
        owner = owner.toOwner()
    )

fun Product.toSynqPayload() =
    SynqProductPayload(
        productId = hostId,
        owner = owner.toSynqOwner(),
        barcode = SynqProductPayload.Barcode(hostId),
        productCategory = category,
        productUom = SynqProductPayload.ProductUom(packaging.toSynqPackaging()),
        confidential = false,
        hostName = hostName.name
    )

fun SynqPackaging.toPackaging(): Packaging =
    when (this) {
        OBJ -> Packaging.NONE
        ESK -> Packaging.BOX
    }

fun SynqOwner.toOwner(): Owner =
    when (this) {
        NB -> Owner.NB
    }

fun Owner.toSynqOwner(): SynqOwner =
    when (this) {
        Owner.NB -> NB
    }

fun Packaging.toSynqPackaging(): SynqPackaging =
    when (this) {
        Packaging.NONE -> OBJ
        Packaging.BOX -> ESK
    }
