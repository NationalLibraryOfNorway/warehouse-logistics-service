package no.nb.mlt.wls.infrastructure

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
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
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.testItem
import no.nb.mlt.wls.testOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StorageEventProcessorTest {


////////////////////////////////////////////////////////////////////////////////
///////////////////////////////  Test Functions  ///////////////////////////////
////////////////////////////////////////////////////////////////////////////////


    @Test
    fun `OrderCreated should mark as processed when successful`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItems(HostName.AXIELL, listOf(testItem.hostId, "testItem-02")) } returns testItemList
            coEvery { getItems(HostName.AXIELL, listOf(testItem.hostId)) } returns listOf(testItemList[0])
            coEvery { getItems(HostName.AXIELL, listOf("testItem-02")) } returns listOf(testItemList[1])
        }

        val messageProcessor = StorageEventProcessorAdapter(
            storageEventRepository = storageMessageRepoMock,
            storageSystems = listOf(happyStorageSystemMock),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        runTest {
            val event = OrderCreated(testOrder)
            messageProcessor.handleEvent(event)
            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyStorageSystemMock.createOrder(any()) }
        }
    }

    @Test
    fun `OrderCreated should send mail when successful`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItems(HostName.AXIELL, listOf(testItem.hostId, "testItem-02")) } returns testItemList
        }

        val messageProcessor = StorageEventProcessorAdapter(
            storageEventRepository = storageMessageRepoMock,
            storageSystems = emptyList(),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        runTest {
            messageProcessor.handleEvent(OrderCreated(testOrder))
            assertThat(emailNotifierMock.orderCreatedCount).isEqualTo(1)
        }
    }

    @Test
    fun `OrderCreated Should not mark as processed if anything fails`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItems(HostName.AXIELL, listOf(testItem.hostId, "testItem-02")) } returns testItemList
        }
        val errorMessage = "Some exception when sending to storage system"
        val invalidStorageMock = mockk<StorageSystemFacade> {
            coEvery { canHandleLocation(any()) } returns true
            coEvery {
                createOrder(any())
            } throws NotImplementedError(errorMessage)
        }

        val messageProcessor = StorageEventProcessorAdapter(
            storageEventRepository = storageMessageRepoMock,
            storageSystems = listOf(invalidStorageMock),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        assertThatThrownBy {
            runTest {
                messageProcessor.handleEvent(OrderCreated(testOrder))
            }
        }
        assertThat(storageMessageRepoMock.processed).hasSize(0)
    }

    @Test
    fun `ItemCreated should mark as processed when successful`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItem(testItem.hostName, testItem.hostId) } returns null
        }

        val messageProcessor = StorageEventProcessorAdapter(
            storageEventRepository = storageMessageRepoMock,
            storageSystems = listOf(happyStorageSystemMock),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        runTest {
            val event = ItemCreated(testItem)
            messageProcessor.handleEvent(event)
            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
        }
    }

    @Test
    fun `ItemCreated should fail if item already exists`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItem(testItem.hostName, testItem.hostId) } returns testItem
        }

        val storageSystemMock = mockk<StorageSystemFacade> {
            coEvery { canHandleItem(any()) } returns true
            coEvery { canHandleLocation("valid-location") } returns true
            coEvery { canHandleLocation("invalid-location") } returns false
            coEvery { createItem(any()) } throws DuplicateResourceException("Duplicate product")
        }

        val messageProcessor = StorageEventProcessorAdapter(
            storageEventRepository = storageMessageRepoMock,
            storageSystems = listOf(storageSystemMock),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        runTest {
            val event = ItemCreated(testItem)
            assertThrows<DuplicateResourceException> {
                messageProcessor.handleEvent(event)
            }
            assertThat(storageMessageRepoMock.processed).hasSize(0)
        }
    }

    @Test
    fun `ItemCreated should succeed despite no valid locations existing`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItem(HostName.AXIELL, "mlt-123456") } returns null
        }

        val testItem = testItem.copy(location = "SOMEWHERE")

        val messageProcessor = StorageEventProcessorAdapter(
            storageEventRepository = storageMessageRepoMock,
            storageSystems = listOf(happyStorageSystemMock),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        runTest {
            val event = ItemCreated(testItem)
            messageProcessor.handleEvent(event)
            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
        }
    }

    @Test
    fun `UpdateOrder should mark as processed if successful`() {
        val extendedTestItemList = testItemList.plus(testItem.copy(hostId = "testItem-03"))
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItems(HostName.AXIELL, listOf(testItem.hostId, "testItem-02", "testItem-03")) } returns extendedTestItemList
            coEvery { getItems(HostName.AXIELL, listOf(testItem.hostId)) } returns listOf(extendedTestItemList[0])
            coEvery { getItems(HostName.AXIELL, listOf("testItem-02")) } returns listOf(extendedTestItemList[1])
            coEvery { getItems(HostName.AXIELL, listOf("testItem-03")) } returns listOf(extendedTestItemList[2])
        }

        val expectedOrder = testOrder.copy(
            note = "I want this soon", orderLine = testOrder.orderLine.plus(Order.OrderItem("testItem-03", status = Order.OrderItem.Status.NOT_STARTED))
        )

        val storageSystemMock = mockk<StorageSystemFacade> {
            coEvery { canHandleItem(any()) } returns true
            coEvery { canHandleLocation("SYNQ_WAREHOUSE") } returns true
            coEvery { canHandleLocation("invalid-location") } returns false
            coEvery { updateOrder(expectedOrder) } returns expectedOrder
        }

        val messageProcessor = StorageEventProcessorAdapter(
            storageEventRepository = storageMessageRepoMock,
            storageSystems = listOf(storageSystemMock),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        runTest {
            val event = OrderUpdated(expectedOrder)
            messageProcessor.handleEvent(event)
            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { storageSystemMock.updateOrder(any()) }
        }
    }

    @Test
    fun `DeleteOrder should mark as processed if successful`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItems(HostName.AXIELL, listOf("mlt-12345", "mlt-54321")) } returns testItemList
            coEvery { getItems(HostName.AXIELL, listOf("mlt-12345")) } returns listOf(testItemList[0])
            coEvery { getItems(HostName.AXIELL, listOf("mlt-54321")) } returns listOf(testItemList[1])
        }

        val storageSystemMock = mockk<StorageSystemFacade> {
            coEvery { canHandleItem(any()) } returns true
            coEvery { canHandleLocation("valid-location") } returns true
            coEvery { canHandleLocation("invalid-location") } returns false
            coEvery { deleteOrder(testOrder.hostOrderId, testOrder.hostName) } returns Unit
        }

        val messageProcessor = StorageEventProcessorAdapter(
            storageEventRepository = storageMessageRepoMock,
            storageSystems = listOf(storageSystemMock),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        runTest {
            val event = OrderDeleted(testOrder.hostName, testOrder.hostOrderId)
            messageProcessor.handleEvent(event)
            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { storageSystemMock.deleteOrder(testOrder.hostOrderId, testOrder.hostName) }
        }
    }


////////////////////////////////////////////////////////////////////////////////
////////////////////////////////  Test Helpers  ////////////////////////////////
////////////////////////////////////////////////////////////////////////////////


    private val storageMessageRepoMock = object : EventRepository<StorageEvent> {
        val processed: MutableList<StorageEvent> = mutableListOf()

        override suspend fun save(event: StorageEvent): StorageEvent {
            TODO("Not relevant for testing")
        }

        override suspend fun getAll(): List<StorageEvent> {
            TODO("Not relevant for testing")
        }

        override suspend fun getUnprocessedSortedByCreatedTime() = emptyList<StorageEvent>()

        override suspend fun markAsProcessed(event: StorageEvent): StorageEvent {
            processed.add(event)
            return event
        }
    }

    // Just a happy little storage system mock, he lives right here...
    private val happyStorageSystemMock = mockk<StorageSystemFacade> {
        coEvery { canHandleItem(any()) } returns true
        coEvery { canHandleLocation("SYNQ_WAREHOUSE") } returns true
        coEvery { canHandleLocation("invalid-location") } returns false
        coEvery { createOrder(any()) } returns Unit
        coEvery { createItem(any()) } returns Unit
    }

    // ... and he's got a friend, a big old email notifier mock
    private val emailNotifierMock = object : EmailNotifier {
        var orderCreatedCount = 0

        override suspend fun orderCreated(
            order: Order, orderItems: List<Item>
        ) {
            orderCreatedCount++
        }
    }

    private val testItemList = listOf(testItem, testItem.copy(hostId = "testItem-02"))
}
