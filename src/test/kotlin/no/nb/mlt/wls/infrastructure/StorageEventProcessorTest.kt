package no.nb.mlt.wls.infrastructure

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.OrderUpdated
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.outbound.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StorageEventProcessorTest {
    private lateinit var cut: StorageEventProcessorAdapter

    @BeforeEach
    fun beforeEach() {
        cut =
            StorageEventProcessorAdapter(
                storageEventRepository = storageMessageRepoMock,
                storageSystems = listOf(happyStorageSystemFacadeMock),
                itemRepository = itemRepoMock,
                emailNotifier = emailNotifierMock
            )
    }

    @Test
    fun `OrderCreated event should be marked as processed when successful`() =
        runTest {
            val event = OrderCreated(testOrder)

            cut.handleEvent(event)

            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.createOrder(any()) }
        }

    @Test
    fun `OrderCreated event should send an email when successful`() =
        runTest {
            val event = OrderCreated(testOrder)

            cut.handleEvent(event)

            assertThat(emailNotifierMock.orderCreatedCount).isEqualTo(1)
        }

    @Test
    fun `OrderCreated event should not be marked as processed or send email if anything fails`() =
        runTest {
            val errorMessage = "Some exception when sending to storage system"
            coEvery { happyStorageSystemFacadeMock.createOrder(any()) } throws NotImplementedError(errorMessage)

            assertThrows<NotImplementedError>(message = errorMessage) {
                cut.handleEvent(OrderCreated(testOrder))
            }

            assertThat(storageMessageRepoMock.processed).hasSize(0)
            assertThat(emailNotifierMock.orderCreatedCount).isEqualTo(0)
        }

    @Test
    fun `ItemCreated event should be marked as processed when successful`() =
        runTest {
            val event = ItemCreated(testItem1)

            cut.handleEvent(event)

            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.createItem(any()) }
        }

    @Test
    fun `ItemCreated event should fail if item already exists`() =
        runTest {
            coEvery { happyStorageSystemFacadeMock.createItem(any()) } throws DuplicateResourceException("Duplicate product")

            assertThrows<DuplicateResourceException>(message = "Duplicate product") {
                cut.handleEvent(ItemCreated(testItem1))
            }

            assertThat(storageMessageRepoMock.processed).hasSize(0)
        }

    @Test
    fun `ItemCreated should succeed despite no valid locations existing`() =
        runTest {
            val event = ItemCreated(createTestItem(location = "invalid-location"))

            cut.handleEvent(event)

            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.createItem(any()) }
        }

    @Test
    fun `DeleteOrder should mark as processed if successful`() =
        runTest {
            val event = OrderDeleted(testOrder.hostName, testOrder.hostOrderId)

            cut.handleEvent(event)

            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.deleteOrder(testOrder.hostOrderId, testOrder.hostName) }
        }

    @Test
    fun `UpdateOrder event should be marked as processed if successful`() =
        runTest {
            val updatedOrder =
                createTestOrder(
                    note = "I want this soon",
                    orderLine = testItemListLarge.map { Order.OrderItem(it.hostId, Order.OrderItem.Status.NOT_STARTED) }
                )
            val event = OrderUpdated(updatedOrder)

            cut.handleEvent(event)

            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.updateOrder(any()) }
        }

    @Test
    fun `Events get grouped and if one group fails, other gets processed`() =
        runTest {
            // Test data
            val createdOrder1 = createTestOrder(hostOrderId = "order1")
            val updatedOrder1 =
                createdOrder1.copy(
                    orderLine = testItemListLarge.map { Order.OrderItem(it.hostId, Order.OrderItem.Status.NOT_STARTED) }
                )
            val createdOrder2 = createTestOrder(hostOrderId = "order2")

            // Test events, grouped visually to show how they should be grouped
            val order1event1 = OrderCreated(createdOrder1)
            val order1event2 = OrderUpdated(updatedOrder1)
            val order1event3 = OrderDeleted(createdOrder1.hostName, createdOrder1.hostOrderId)

            val order2event1 = OrderCreated(createdOrder2)
            val order2event2 = OrderDeleted(createdOrder2.hostName, createdOrder2.hostOrderId)

            val item1event = ItemCreated(testItem1)

            val item2event = ItemCreated(testItem2)

            val item3event = ItemCreated(testItem3)

            // Make happyStorageSystemMock fail in during order1 update, and during item 2 creation
            coEvery { happyStorageSystemFacadeMock.updateOrder(updatedOrder1) } throws StorageSystemException("Could not update order")
            coEvery { happyStorageSystemFacadeMock.createItem(testItem2) } throws DuplicateResourceException("Duplicate product")

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
            assertThat(storageMessageRepoMock.processed)
                .hasSize(5)
                .contains(order1event1, order2event1, order2event2, item1event, item3event)

            // Make sure every event got called
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.createOrder(createdOrder1) }
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.updateOrder(updatedOrder1) }
            // Delete should not be called as update order above should fail
            coVerify(exactly = 0) { happyStorageSystemFacadeMock.deleteOrder(createdOrder1.hostOrderId, createdOrder1.hostName) }

            coVerify(exactly = 1) { happyStorageSystemFacadeMock.createOrder(createdOrder2) }
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.deleteOrder(createdOrder2.hostOrderId, createdOrder2.hostName) }

            coVerify(exactly = 1) { happyStorageSystemFacadeMock.createItem(testItem1) }
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.createItem(testItem2) }
            coVerify(exactly = 1) { happyStorageSystemFacadeMock.createItem(testItem3) }
        }

    private val testItem1 = createTestItem()
    private val testItem2 = createTestItem(hostId = "testItem-02")
    private val testItem3 = createTestItem(hostId = "testItem-03")

    private val testItemListSmall = listOf(testItem1, testItem2)
    private val testItemListLarge = listOf(testItem1, testItem2, testItem3)

    private val testOrder = createTestOrder()

    private val unprocessedEventsList = mutableListOf<StorageEvent>()

    private val storageMessageRepoMock =
        object : EventRepository<StorageEvent> {
            val processed: MutableList<StorageEvent> = mutableListOf()

            override suspend fun save(event: StorageEvent): StorageEvent {
                TODO("Not relevant for testing")
            }

            override suspend fun getAll(): List<StorageEvent> {
                TODO("Not relevant for testing")
            }

            override suspend fun getUnprocessedSortedByCreatedTime() = unprocessedEventsList

            override suspend fun markAsProcessed(event: StorageEvent): StorageEvent {
                processed.add(event)
                return event
            }
        }

    // A happy little storage system mock, he lives right here...
    private val happyStorageSystemFacadeMock =
        mockk<StorageSystemFacade> {
            coEvery { canHandleItem(any()) } returns true
            coEvery { canHandleLocation("invalid-location") } returns false
            coEvery { canHandleLocation("SYNQ_WAREHOUSE") } returns true
            coEvery { canHandleLocation("WITH_LENDER") } returns true
            coEvery { deleteOrder(any(), any()) } returns Unit
            coEvery { updateOrder(any()) } returns testOrder
            coEvery { createOrder(any()) } returns Unit
            coEvery { createItem(any()) } returns Unit
        }

    // ... and he's got a friend, a big old email notifier mock...
    private val emailNotifierMock =
        object : EmailNotifier {
            var orderCreatedCount = 0
            var order: Order? = null

            override suspend fun orderCreated(
                order: Order,
                orderItems: List<Item>
            ) {
                orderCreatedCount++
                this.order = order
            }
        }

    // ... and this itemRepoMock over here will be our little secret
    private val itemRepoMock =
        mockk<ItemRepository> {
            coEvery { getItems(testItem1.hostName, listOf(testItem1.hostId, testItem2.hostId, testItem3.hostId)) } returns testItemListLarge
            coEvery { getItems(testItem1.hostName, listOf(testItem1.hostId, testItem2.hostId)) } returns testItemListSmall
            coEvery { getItem(testItem1.hostName, testItem1.hostId) } returns testItem1
            coEvery { getItem(testItem2.hostName, testItem2.hostId) } returns testItem2
            coEvery { getItem(testItem3.hostName, testItem3.hostId) } returns testItem3
        }
}
