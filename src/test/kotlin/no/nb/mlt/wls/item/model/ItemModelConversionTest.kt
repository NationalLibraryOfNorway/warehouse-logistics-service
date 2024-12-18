package no.nb.mlt.wls.item.model

import no.nb.mlt.wls.application.hostapi.item.ApiItemPayload
import no.nb.mlt.wls.application.hostapi.item.toApiPayload
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.infrastructure.callbacks.NotificationItemPayload
import no.nb.mlt.wls.infrastructure.callbacks.toNotificationItemPayload
import no.nb.mlt.wls.infrastructure.synq.SynqOwner
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload
import no.nb.mlt.wls.infrastructure.synq.toSynqCategory
import no.nb.mlt.wls.infrastructure.synq.toSynqPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ItemModelConversionTest {
    private val testItemPayload =
        ApiItemPayload(
            hostId = "mlt-test-1234",
            hostName = HostName.AXIELL,
            description = "Tyven skal du hete",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            callbackUrl = "https://callback-wls.no/item",
            location = "",
            quantity = 1
        )

    private val testItem =
        Item(
            hostId = "mlt-test-1234",
            hostName = HostName.AXIELL,
            description = "Tyven skal du hete",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            callbackUrl = "https://callback-wls.no/item",
            location = "",
            quantity = 1
        )

    private val testSynqPayload =
        SynqProductPayload(
            productId = "mlt-test-1234",
            owner = SynqOwner.NB,
            barcode = SynqProductPayload.Barcode("mlt-test-1234"),
            description = "Tyven skal du hete",
            productCategory = toSynqCategory(ItemCategory.PAPER),
            productUom = SynqProductPayload.ProductUom(SynqProductPayload.SynqPackaging.OBJ),
            false,
            hostName = HostName.AXIELL.toString()
        )

    private val testItemNotificationPayload =
        NotificationItemPayload(
            hostId = "mlt-test-1234",
            hostName = HostName.AXIELL,
            description = "Tyven skal du hete",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            callbackUrl = "https://callback-wls.no/item",
            location = "",
            quantity = 1
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
    fun `item converts to notification payload`() {
        val payload = testItem.toNotificationItemPayload()
        assertThat(payload.hostId).isEqualTo(testItemNotificationPayload.hostId)
        assertThat(payload.hostName).isEqualTo(testItemNotificationPayload.hostName)
        assertThat(payload.description).isEqualTo(testItemNotificationPayload.description)
        assertThat(payload.itemCategory).isEqualTo(testItemNotificationPayload.itemCategory)
        assertThat(payload.preferredEnvironment).isEqualTo(testItemNotificationPayload.preferredEnvironment)
        assertThat(payload.packaging).isEqualTo(testItemNotificationPayload.packaging)
        assertThat(payload.callbackUrl).isEqualTo(testItemNotificationPayload.callbackUrl)
        assertThat(payload.location).isEqualTo(testItemNotificationPayload.location)
        assertThat(payload.quantity).isEqualTo(testItemNotificationPayload.quantity)
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
        assertThat(item.callbackUrl).isEqualTo(testItem.callbackUrl)
        assertThat(item.location).isEqualTo(testItem.location)
        assertThat(item.quantity).isEqualTo(testItem.quantity)
    }
}
