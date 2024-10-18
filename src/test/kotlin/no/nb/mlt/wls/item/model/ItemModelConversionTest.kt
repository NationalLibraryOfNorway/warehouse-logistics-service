package no.nb.mlt.wls.item.model

import no.nb.mlt.wls.application.hostapi.item.ApiItemPayload
import no.nb.mlt.wls.application.hostapi.item.toApiPayload
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.infrastructure.synq.SynqOwner
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload
import no.nb.mlt.wls.infrastructure.synq.toSynqPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ItemModelConversionTest {
    private val testItemPayload =
        ApiItemPayload(
            hostId = "mlt-test-1234",
            hostName = HostName.AXIELL,
            description = "Tyven skal du hete",
            itemCategory = "NONE",
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            owner = Owner.NB,
            callbackUrl = "https://callback.com/item",
            location = "",
            quantity = 1.0
        )

    private val testItem =
        Item(
            hostId = "mlt-test-1234",
            hostName = HostName.AXIELL,
            description = "Tyven skal du hete",
            itemCategory = "NONE",
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            owner = Owner.NB,
            callbackUrl = "https://callback.com/item",
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
    fun `item converts to API payload`() {
        val payload = testItem.toApiPayload()
        assertThat(payload.hostId).isEqualTo(testItemPayload.hostId)
        assertThat(payload.hostName).isEqualTo(testItemPayload.hostName)
        assertThat(payload.description).isEqualTo(testItemPayload.description)
        assertThat(payload.itemCategory).isEqualTo(testItemPayload.itemCategory)
        assertThat(payload.preferredEnvironment).isEqualTo(testItemPayload.preferredEnvironment)
        assertThat(payload.packaging).isEqualTo(testItemPayload.packaging)
        assertThat(payload.owner).isEqualTo(testItemPayload.owner)
        assertThat(payload.callbackUrl).isEqualTo(testItemPayload.callbackUrl)
        assertThat(payload.location).isEqualTo(testItemPayload.location)
        assertThat(payload.quantity).isEqualTo(testItemPayload.quantity)
    }

    @Test
    fun `item converts to SynQ payload`() {
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
    fun `API payload converts to item`() {
        val item = testItemPayload.toItem()
        assertThat(item.hostId).isEqualTo(testItem.hostId)
        assertThat(item.hostName).isEqualTo(testItem.hostName)
        assertThat(item.description).isEqualTo(testItem.description)
        assertThat(item.itemCategory).isEqualTo(testItem.itemCategory)
        assertThat(item.preferredEnvironment).isEqualTo(testItem.preferredEnvironment)
        assertThat(item.packaging).isEqualTo(testItem.packaging)
        assertThat(item.owner).isEqualTo(testItem.owner)
        assertThat(item.callbackUrl).isEqualTo(testItem.callbackUrl)
        assertThat(item.location).isEqualTo(testItem.location)
        assertThat(item.quantity).isEqualTo(testItem.quantity)
    }
}
