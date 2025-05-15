package no.nb.mlt.wls.infrastructure

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cloud.gateway.support.TimeoutException

class CatalogEventProcessorTest {
    private lateinit var cut: CatalogEventProcessorAdapter

    @BeforeEach
    fun beforeEach() {
        cut = CatalogEventProcessorAdapter(catalogMessageRepoMock, happyInventoryNotifierMock)
    }

    @Test
    fun `OrderUpdate event should call inventory notifier and be marked as processed`() =
        runTest {
            val event = OrderEvent(testOrder)

            cut.handleEvent(event)

            assertThat(catalogMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(any(), event.eventTimestamp, event.id) }
        }

    @Test
    fun `OrderUpdate event should handle error from inventory notifier`() =
        runTest {
            coEvery { happyInventoryNotifierMock.orderChanged(any(), any(), any()) } throws TimeoutException("Timed out")
            val event = OrderEvent(testOrder)

            assertThrows<TimeoutException> { cut.handleEvent(event) }

            assertThat(catalogMessageRepoMock.processed).hasSize(0).doesNotContain(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(any(), event.eventTimestamp, event.id) }
        }

    @Test
    fun `ItemUpdate event should call inventory notifier and be marked as processed`() =
        runTest {
            val event = ItemEvent(testItem)

            cut.handleEvent(event)

            assertThat(catalogMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(any(), event.eventTimestamp, event.id) }
        }

    @Test
    fun `Item Update event should handle error from inventory notifier`() =
        runTest {
            coEvery { happyInventoryNotifierMock.itemChanged(any(), any(), any()) } throws TimeoutException("Timed out")
            val event = ItemEvent(testItem)

            assertThrows<TimeoutException> { cut.handleEvent(event) }

            assertThat(catalogMessageRepoMock.processed).hasSize(0).doesNotContain(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(any(), event.eventTimestamp, event.id) }
        }

    @Test
    fun `Events get grouped and if one group fails, other gets processed`() =
        runTest {
            // Test data
            val order1 = createTestOrder(hostOrderId = "order1")
            val order2 = createTestOrder(hostOrderId = "order2")
            val item1 = createTestItem(hostId = "item1")
            val item2 = createTestItem(hostId = "item2")
            val item3 = createTestItem(hostId = "item3")

            // Test events, grouped visually to show how they should be grouped
            val order1event1 = OrderEvent(order1)
            val order1event2 = OrderEvent(order1.copy(status = Order.Status.IN_PROGRESS))
            val order1event3 = OrderEvent(order1.copy(status = Order.Status.COMPLETED))

            val order2event1 = OrderEvent(order2)
            val order2event2 = OrderEvent(order2.copy(status = Order.Status.DELETED))

            val item1event = ItemEvent(item1)

            val item2event = ItemEvent(item2)

            val item3event = ItemEvent(item3)

            // Make happyStorageSystemMock fail in during order1 update, and during item 2 creation
            coEvery {
                happyInventoryNotifierMock.orderChanged(order1event2.order, order1event2.eventTimestamp, order1event2.id)
            } throws TimeoutException("Timed out")
            coEvery {
                happyInventoryNotifierMock.itemChanged(
                    item2event.item,
                    item2event.eventTimestamp,
                    item2event.id
                )
            } throws TimeoutException("Timed out")

            // Create list of unprocessed events, mixing them, making sure order2 events happen "after" order1 fails, mix in items all over the list
            unprocessedEventsList.addAll(
                listOf(
                    order1event1,
                    item1event,
                    order1event2,
                    order2event1,
                    order1event3,
                    item2event,
                    order2event2,
                    item3event
                )
            )

            // Process every event waiting its turn
            cut.processOutbox()

            // Order1 has 1 processed event, Order2 has 2, Item 1 and 3 have one each --> 5
            assertThat(catalogMessageRepoMock.processed)
                .hasSize(5)
                .contains(order1event1, order2event1, order2event2, item1event, item3event)

            // Make sure every event got called
            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(order1event1.order, order1event1.eventTimestamp, order1event1.id) }
            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(order1event2.order, order1event2.eventTimestamp, order1event2.id) }
            // Should not be called as update above should fail
            coVerify(exactly = 0) { happyInventoryNotifierMock.orderChanged(order1event3.order, order1event3.eventTimestamp, order1event3.id) }

            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(order2event1.order, order2event1.eventTimestamp, order2event1.id) }
            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(order2event2.order, order2event2.eventTimestamp, order2event2.id) }

            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(item1event.item, item1event.eventTimestamp, item1event.id) }
            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(item2event.item, item2event.eventTimestamp, item2event.id) }
            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(item3event.item, item3event.eventTimestamp, item3event.id) }
        }

    private val testItem = createTestItem()

    private val testOrder = createTestOrder()

    private val unprocessedEventsList = mutableListOf<CatalogEvent>()

    private val catalogMessageRepoMock =
        object : EventRepository<CatalogEvent> {
            val processed: MutableList<CatalogEvent> = mutableListOf()

            override suspend fun save(event: CatalogEvent): CatalogEvent {
                TODO("Not relevant for testing")
            }

            override suspend fun getAll(): List<CatalogEvent> {
                TODO("Not relevant for testing")
            }

            override suspend fun getUnprocessedSortedByCreatedTime() = unprocessedEventsList

            override suspend fun markAsProcessed(event: CatalogEvent): CatalogEvent {
                processed.add(event)
                return event
            }
        }

    private val happyInventoryNotifierMock =
        mockk<InventoryNotifier> {
            coEvery { itemChanged(any(), any(), any()) } returns Unit
            coEvery { orderChanged(any(), any(), any()) } returns Unit
        }
}
