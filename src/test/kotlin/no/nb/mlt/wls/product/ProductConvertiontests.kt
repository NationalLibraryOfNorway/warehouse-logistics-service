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
import org.assertj.core.api.Assertions.assertThat
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
        assertThat(payload.hostId).isEqualTo(testProductPayload.hostId)
        assertThat(payload.hostName).isEqualTo(testProductPayload.hostName)
        assertThat(payload.description).isEqualTo(testProductPayload.description)
        assertThat(payload.productCategory).isEqualTo(testProductPayload.productCategory)
        assertThat(payload.preferredEnvironment).isEqualTo(testProductPayload.preferredEnvironment)
        assertThat(payload.packaging).isEqualTo(testProductPayload.packaging)
        assertThat(payload.owner).isEqualTo(testProductPayload.owner)
        assertThat(payload.location).isEqualTo(testProductPayload.location)
        assertThat(payload.quantity).isEqualTo(testProductPayload.quantity)
    }

    @Test
    fun `product converts to SynQ payload`() {
        val synqPayload = testProduct.toSynqPayload()
        assertThat(synqPayload.hostName).isEqualTo(testSynqPayload.hostName)
        assertThat(synqPayload.productId).isEqualTo(testSynqPayload.productId)
        assertThat(synqPayload.productUom.uomId).isEqualTo(testSynqPayload.productUom.uomId)
        assertThat(synqPayload.productCategory).isEqualTo(testSynqPayload.productCategory)
        assertThat(synqPayload.barcode.barcodeId).isEqualTo(testSynqPayload.barcode.barcodeId)
        assertThat(synqPayload.owner).isEqualTo(testSynqPayload.owner)
        assertThat(synqPayload.description).isEqualTo(testSynqPayload.description)
    }

    @Test
    fun `payload converts to product`() {
        val product = testProductPayload.toProduct()
        assertThat(product.hostId).isEqualTo(testProduct.hostId)
        assertThat(product.hostName).isEqualTo(testProduct.hostName)
        assertThat(product.description).isEqualTo(testProduct.description)
        assertThat(product.productCategory).isEqualTo(testProduct.productCategory)
        assertThat(product.preferredEnvironment).isEqualTo(testProduct.preferredEnvironment)
        assertThat(product.packaging).isEqualTo(testProduct.packaging)
        assertThat(product.owner).isEqualTo(testProduct.owner)
        assertThat(product.location).isEqualTo(testProduct.location)
        assertThat(product.quantity).isEqualTo(testProduct.quantity)
    }

    @Test
    fun `payload converts to SynQ payload`() {
        val synq = testProductPayload.toProduct().toSynqPayload()
        assertThat(synq.hostName).isEqualTo(testSynqPayload.hostName)
        assertThat(synq.productId).isEqualTo(testSynqPayload.productId)
        assertThat(synq.productUom.uomId).isEqualTo(testSynqPayload.productUom.uomId)
        assertThat(synq.productCategory).isEqualTo(testSynqPayload.productCategory)
        assertThat(synq.barcode.barcodeId).isEqualTo(testSynqPayload.barcode.barcodeId)
        assertThat(synq.owner).isEqualTo(testSynqPayload.owner)
        assertThat(synq.description).isEqualTo(testSynqPayload.description)
    }
}
