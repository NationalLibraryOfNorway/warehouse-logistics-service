package no.nb.mlt.wls.product

import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.core.data.synq.SynqOwner
import no.nb.mlt.wls.product.model.Product
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.payloads.SynqProductPayload
import no.nb.mlt.wls.product.payloads.toApiPayload
import no.nb.mlt.wls.product.payloads.toProduct
import no.nb.mlt.wls.product.payloads.toSynqPayload
import org.junit.jupiter.api.Test

class ProductConvertiontests {
    val testProductPayload =
        ApiProductPayload(
            hostId = "mlt-test-1234",
            hostName = HostName.AXIELL,
            description = "Tyven skal du hete",
            productCategory = "NONE",
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            owner = Owner.NB,
            location = "",
            quantity = 1.0
        )

    val testProduct =
        Product(
            hostId = "mlt-test-1234",
            hostName = HostName.AXIELL,
            description = "Tyven skal du hete",
            productCategory = "NONE",
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            owner = Owner.NB,
            location = "",
            quantity = 1.0
        )

    val testSynqPayload =
        SynqProductPayload(
            productId = "mlt-test-1234",
            owner = SynqOwner.NB,
            barcode = SynqProductPayload.Barcode("mlt-test-1234"),
            description = "Tyven skal du hete",
            productCategory = "NONE",
            productUom = SynqProductPayload.ProductUom(SynqProductPayload.SynqPackaging.OBJ),
            false,
            hostName = HostName.AXIELL.toString()
        )

    @Test
    fun `product converts to payload`() {
        val payload = testProduct.toApiPayload()
        assert(payload.hostId == testProductPayload.hostId)
        assert(payload.hostName == testProductPayload.hostName)
        assert(payload.description == testProductPayload.description)
        assert(payload.productCategory == testProductPayload.productCategory)
        assert(payload.preferredEnvironment == testProductPayload.preferredEnvironment)
        assert(payload.packaging == testProductPayload.packaging)
        assert(payload.owner == testProductPayload.owner)
        assert(payload.location == testProductPayload.location)
        assert(payload.quantity == testProductPayload.quantity)
    }

    @Test
    fun `product converts to SynQ payload`() {
        val synqPayload = testProduct.toSynqPayload()
        assert(synqPayload.hostName == testSynqPayload.hostName)
        assert(synqPayload.productId == testSynqPayload.productId)
        assert(synqPayload.productUom.uomId == testSynqPayload.productUom.uomId)
        assert(synqPayload.productCategory == testSynqPayload.productCategory)
        assert(synqPayload.barcode.barcodeId == testSynqPayload.barcode.barcodeId)
        assert(synqPayload.owner == testSynqPayload.owner)
        assert(synqPayload.description == testSynqPayload.description)
    }

    @Test
    fun `payload converts to product`() {
        val product = testProductPayload.toProduct()
        assert(product.hostId == testProduct.hostId)
        assert(product.hostName == testProduct.hostName)
        assert(product.description == testProduct.description)
        assert(product.productCategory == testProduct.productCategory)
        assert(product.preferredEnvironment == testProduct.preferredEnvironment)
        assert(product.packaging == testProduct.packaging)
        assert(product.owner == testProduct.owner)
        assert(product.location == testProduct.location)
        assert(product.quantity == testProduct.quantity)
    }

    @Test
    fun `payload converts to SynQ payload`() {
        val synq = testProductPayload.toProduct().toSynqPayload()
        assert(synq.hostName == testSynqPayload.hostName)
        assert(synq.productId == testSynqPayload.productId)
        assert(synq.productUom.uomId == testSynqPayload.productUom.uomId)
        assert(synq.productCategory == testSynqPayload.productCategory)
        assert(synq.barcode.barcodeId == testSynqPayload.barcode.barcodeId)
        assert(synq.owner == testSynqPayload.owner)
        assert(synq.description == testSynqPayload.description)
    }
}
