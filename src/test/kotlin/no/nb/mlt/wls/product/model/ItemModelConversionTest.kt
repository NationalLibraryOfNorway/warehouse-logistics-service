package no.nb.mlt.wls.product.model

import no.nb.mlt.wls.application.restapi.product.ApiProductPayload
import no.nb.mlt.wls.application.restapi.product.toApiPayload
import no.nb.mlt.wls.application.restapi.product.toProduct
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.infrastructure.synq.SynqOwner
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload
import no.nb.mlt.wls.infrastructure.synq.toSynqPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ItemModelConversionTest {
    private val testProductPayload =
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

    private val testItem =
        Item(
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

    private val testSynqPayload =
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
    fun `product converts to API payload`() {
        val payload = testItem.toApiPayload()
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
        val synqPayload = testItem.toSynqPayload()
        assertThat(synqPayload.hostName).isEqualTo(testSynqPayload.hostName)
        assertThat(synqPayload.productId).isEqualTo(testSynqPayload.productId)
        assertThat(synqPayload.productUom.uomId).isEqualTo(testSynqPayload.productUom.uomId)
        assertThat(synqPayload.productCategory).isEqualTo(testSynqPayload.productCategory)
        assertThat(synqPayload.barcode.barcodeId).isEqualTo(testSynqPayload.barcode.barcodeId)
        assertThat(synqPayload.owner).isEqualTo(testSynqPayload.owner)
        assertThat(synqPayload.description).isEqualTo(testSynqPayload.description)
    }

    @Test
    fun `API payload converts to product`() {
        val product = testProductPayload.toProduct()
        assertThat(product.hostId).isEqualTo(testItem.hostId)
        assertThat(product.hostName).isEqualTo(testItem.hostName)
        assertThat(product.description).isEqualTo(testItem.description)
        assertThat(product.productCategory).isEqualTo(testItem.productCategory)
        assertThat(product.preferredEnvironment).isEqualTo(testItem.preferredEnvironment)
        assertThat(product.packaging).isEqualTo(testItem.packaging)
        assertThat(product.owner).isEqualTo(testItem.owner)
        assertThat(product.location).isEqualTo(testItem.location)
        assertThat(product.quantity).isEqualTo(testItem.quantity)
    }
}
