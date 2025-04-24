package no.nb.mlt.wls.synq.model

import no.nb.mlt.wls.application.synqapi.synq.AttributeValue
import no.nb.mlt.wls.application.synqapi.synq.Position
import no.nb.mlt.wls.application.synqapi.synq.Product
import no.nb.mlt.wls.application.synqapi.synq.SynqBatchMoveItemPayload
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatus
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatusUpdatePayload
import no.nb.mlt.wls.application.synqapi.synq.getConvertedStatus
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.infrastructure.synq.toSynqHostname
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SynqModelConversionTest {
    @Test
    fun `SynqOrderStatus maps correctly to an OrderStatus`() {
        val notStarted = synqOrderStatusUpdatePayload.copy(status = SynqOrderStatus.NEW)
        val completed = synqOrderStatusUpdatePayload.copy(status = SynqOrderStatus.COMPLETED)
        val cancelled = synqOrderStatusUpdatePayload.copy(status = SynqOrderStatus.CANCELLED)
        val allocated = synqOrderStatusUpdatePayload.copy(status = SynqOrderStatus.ALLOCATED)
        val released = synqOrderStatusUpdatePayload.copy(status = SynqOrderStatus.RELEASED)

        assertThat(notStarted.getConvertedStatus()).isEqualTo(Order.Status.NOT_STARTED)
        assertThat(completed.getConvertedStatus()).isEqualTo(Order.Status.COMPLETED)
        assertThat(cancelled.getConvertedStatus()).isEqualTo(Order.Status.DELETED)
        assertThat(allocated.getConvertedStatus()).isEqualTo(Order.Status.IN_PROGRESS)
        assertThat(released.getConvertedStatus()).isEqualTo(Order.Status.IN_PROGRESS)
    }

    @Test
    fun `SynqBatchMoveItemPayload maps correctly to a list of MoveItemPayloads`() {
        val moveItemPayloads = synqBatchMoveItemPayload.loadUnit.map {
            it.toMoveItemPayload(synqBatchMoveItemPayload.location)
        }

        assertThat(moveItemPayloads).hasSize(3)
        assertThat(moveItemPayloads[0].hostName).isEqualTo(HostName.AXIELL)
        assertThat(moveItemPayloads[0].hostId).isEqualTo(product.productId)
        assertThat(moveItemPayloads[0].quantity).isEqualTo(1)
        assertThat(moveItemPayloads[0].location).isEqualTo(synqBatchMoveItemPayload.location)

        val oneProduct = product.toMoveItemPayload(synqBatchMoveItemPayload.location)

        assertThat(oneProduct.hostName).isEqualTo(HostName.AXIELL)
        assertThat(oneProduct.hostId).isEqualTo(product.productId)
        assertThat(oneProduct.quantity).isEqualTo(1)
        assertThat(oneProduct.location).isEqualTo(synqBatchMoveItemPayload.location)
    }

    @Test
    fun `SynqBatchMoveItemPayload maps correctly to a list of UpdateItemPayloads`() {
        val updateItemPayloads = synqBatchUpdateItemPayload.loadUnit.map {
            it.toUpdateItemPayload(synqBatchMoveItemPayload.prevLocation, synqBatchUpdateItemPayload.location)
        }

        assertThat(updateItemPayloads).hasSize(3)
        assertThat(updateItemPayloads[0].hostName).isEqualTo(HostName.AXIELL)
        assertThat(updateItemPayloads[0].hostId).isEqualTo(product.productId)
        assertThat(updateItemPayloads[0].quantity).isEqualTo(1)
        assertThat(updateItemPayloads[0].location).isEqualTo(synqBatchMoveItemPayload.location)

        val oneProduct = product.toMoveItemPayload(synqBatchMoveItemPayload.location)

        assertThat(oneProduct.hostName).isEqualTo(HostName.AXIELL)
        assertThat(oneProduct.hostId).isEqualTo(product.productId)
        assertThat(oneProduct.quantity).isEqualTo(1)
        assertThat(oneProduct.location).isEqualTo(synqBatchUpdateItemPayload.location)
    }

    private val synqOrderStatusUpdatePayload =
        SynqOrderStatusUpdatePayload(
            prevStatus = SynqOrderStatus.PICKED,
            status = SynqOrderStatus.COMPLETED,
            hostName = toSynqHostname(HostName.AXIELL),
            warehouse = "Sikringmagasin_2"
        )

    private val product =
        Product(
            confidentialProduct = false,
            hostName = toSynqHostname(createTestItem().hostName),
            productId = createTestItem().hostId,
            productOwner = "NB",
            productVersionId = "Default",
            quantityOnHand = null,
            quantityMove = 1,
            suspect = false,
            attributeValue =
                listOf(
                    AttributeValue(
                        name = "materialStatus",
                        value = "Available"
                    )
                ),
            position =
                Position(
                    xPosition = 1,
                    yPosition = 1,
                    zPosition = 1
                )
        )

    private val movedProduct =
        Product(
            confidentialProduct = false,
            hostName = toSynqHostname(createTestItem().hostName),
            productId = createTestItem().hostId,
            productOwner = "NB",
            productVersionId = "Default",
            quantityOnHand = 1,
            quantityMove = null,
            suspect = false,
            attributeValue =
                listOf(
                    AttributeValue(
                        name = "materialStatus",
                        value = "Available"
                    )
                ),
            position =
                Position(
                    xPosition = 1,
                    yPosition = 1,
                    zPosition = 1
                )
        )

    private val synqBatchMoveItemPayload =
        SynqBatchMoveItemPayload(
            tuId = "6942066642",
            location = "SYNQ_WAREHOUSE",
            prevLocation = "WS_PLUKKSENTER_1",
            loadUnit = listOf(product, product, product),
            user = "per.person@nb.no",
            warehouse = "Sikringmagasin_2"
        )

    private val synqBatchUpdateItemPayload =
        SynqBatchMoveItemPayload(
            tuId = "6942066642",
            location = "SYNQ_WAREHOUSE",
            prevLocation = "WS_PLUKKSENTER_1",
            loadUnit = listOf(movedProduct, movedProduct, movedProduct),
            user = "per.person@nb.no",
            warehouse = "Sikringmagasin_2"
        )
}
