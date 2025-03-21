package no.nb.mlt.wls.infrastructure

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.catalogEvents.CatalogEvent
import no.nb.mlt.wls.domain.model.catalogEvents.ItemEvent
import no.nb.mlt.wls.domain.model.catalogEvents.OrderEvent
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cloud.gateway.support.TimeoutException
import java.time.Instant

class CatalogEventProcessorTest {
    private val catalogMessageRepoMock =
        object : EventRepository<CatalogEvent> {
            val processed: MutableList<CatalogEvent> = mutableListOf()

            override suspend fun save(event: CatalogEvent): CatalogEvent {
                TODO("Not yet implemented")
            }

            override suspend fun getAll(): List<CatalogEvent> {
                TODO("Not yet implemented")
            }

            override suspend fun getUnprocessedSortedByCreatedTime() = emptyList<CatalogEvent>()

            override suspend fun markAsProcessed(event: CatalogEvent): CatalogEvent {
                processed.add(event)
                return event
            }
        }

    private val happyInventoryNotifierMock =
        mockk<InventoryNotifier> {
            coEvery { itemChanged(any(), any()) } returns Unit
            coEvery { orderChanged(any(), any()) } returns Unit
        }

    @Test
    fun `order update should call inventory notifier and mark message as processed`() {
        val messageProcessor =
            CatalogEventProcessorAdapter(
                catalogEventRepository = catalogMessageRepoMock,
                inventoryNotifier = happyInventoryNotifierMock
            )

        runTest {
            val event = OrderEvent(testOrder, eventTimestamp = Instant.now())
            messageProcessor.handleEvent(event)
            assertThat(catalogMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(any(), event.eventTimestamp) }
        }
    }

    @Test
    fun `order update should handle error from inventory notifier`() {
        coEvery { happyInventoryNotifierMock.orderChanged(any(), any()) } throws TimeoutException("Timed out")

        val messageProcessor =
            CatalogEventProcessorAdapter(
                catalogEventRepository = catalogMessageRepoMock,
                inventoryNotifier = happyInventoryNotifierMock
            )

        runTest {
            val event = OrderEvent(testOrder, eventTimestamp = Instant.now())
            assertThrows<TimeoutException> { messageProcessor.handleEvent(event) }
            assertThat(catalogMessageRepoMock.processed).hasSize(0).doesNotContain(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(any(), event.eventTimestamp) }
        }
    }

    @Test
    fun `item update should call inventory notifier and mark message as processed`() {
        val messageProcessor =
            CatalogEventProcessorAdapter(
                catalogEventRepository = catalogMessageRepoMock,
                inventoryNotifier = happyInventoryNotifierMock
            )

        runTest {
            val event = ItemEvent(testItem, eventTimestamp = Instant.now())
            messageProcessor.handleEvent(event)
            assertThat(catalogMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(any(), event.eventTimestamp) }
        }
    }

    @Test
    fun `item update should handle error from inventory notifier`() {
        coEvery { happyInventoryNotifierMock.itemChanged(any(), any()) } throws TimeoutException("Timed out")

        val messageProcessor =
            CatalogEventProcessorAdapter(
                catalogEventRepository = catalogMessageRepoMock,
                inventoryNotifier = happyInventoryNotifierMock
            )

        runTest {
            val event = ItemEvent(testItem, eventTimestamp = Instant.now())
            assertThrows<TimeoutException> { messageProcessor.handleEvent(event) }
            assertThat(catalogMessageRepoMock.processed).hasSize(0).doesNotContain(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(any(), event.eventTimestamp) }
        }
    }

// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// Test Help //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    private val testOrder =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "mlt-12345-order",
            status = Order.Status.NOT_STARTED,
            orderLine =
                listOf(
                    Order.OrderItem("mlt-12345", Order.OrderItem.Status.NOT_STARTED),
                    Order.OrderItem("mlt-54321", Order.OrderItem.Status.NOT_STARTED)
                ),
            orderType = Order.Type.LOAN,
            address = null,
            contactPerson = "contactPerson",
            contactEmail = "contact@ema.il",
            note = null,
            callbackUrl = "https://callback-wls.no/order"
        )

    private val testItem =
        Item(
            hostName = HostName.AXIELL,
            hostId = "mlt-12345",
            description = "description",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            callbackUrl = "https://callback-wls.no/item",
            location = "location",
            quantity = 1
        )
}
