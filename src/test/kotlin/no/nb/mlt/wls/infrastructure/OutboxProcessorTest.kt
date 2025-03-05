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
import no.nb.mlt.wls.domain.model.outboxMessages.ItemCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OrderCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OrderUpdated
import no.nb.mlt.wls.domain.model.outboxMessages.OutboxMessage
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OutboxRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class OutboxProcessorTest {
    private val outboxRepoMock =
        object : OutboxRepository {
            val processed: MutableList<OutboxMessage> = mutableListOf()

            override suspend fun save(outboxMessage: OutboxMessage): OutboxMessage {
                TODO("Not yet implemented")
            }

            override suspend fun getAll(): List<OutboxMessage> {
                TODO("Not yet implemented")
            }

            override suspend fun getUnprocessedSortedByCreatedTime() = emptyList<OrderCreated>()

            override suspend fun markAsProcessed(outboxMessage: OutboxMessage): OutboxMessage {
                processed.add(outboxMessage)
                return outboxMessage
            }
        }

    private val happyStorageSystemMock =
        mockk<StorageSystemFacade> {
            coEvery { canHandleItem(any()) } returns true
            coEvery { canHandleLocation("SOMEWHERE-KNOWN") } returns true
            coEvery { canHandleLocation("SOMEWHERE") } returns false
            coEvery { createOrder(any()) } returns Unit
            coEvery { createItem(any()) } returns Unit
            coEvery { deleteOrder(any()) } returns Unit
        }

    private val emailNotifierMock =
        object : EmailNotifier {
            var orderCreatedCount = 0
            var orderUpdatedCount = 0

            override suspend fun orderCreated(
                order: Order,
                orderItems: List<Item>
            ) {
                orderCreatedCount++
            }

            override suspend fun orderUpdated(order: Order) {
                orderUpdatedCount++
            }
        }

    @Test
    fun `OrderCreated should mark as processed when successful`() {
        val itemRepoMock =
            mockk<ItemRepository> {
                coEvery { getItems(listOf("item-id-1", "item-id-2"), HostName.AXIELL) } returns testItemList
                coEvery { getItems(listOf("item-id-1"), HostName.AXIELL) } returns listOf(testItemList[0])
                coEvery { getItems(listOf("item-id-2"), HostName.AXIELL) } returns listOf(testItemList[1])
            }

        val outboxProcessor =
            OutboxProcessor(
                outboxRepository = outboxRepoMock,
                storageSystems = listOf(happyStorageSystemMock),
                itemRepository = itemRepoMock,
                emailNotifier = emailNotifierMock
            )

        runTest {
            val event = OrderCreated(testOrder)
            outboxProcessor.handleEvent(event)
            assertThat(outboxRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyStorageSystemMock.createOrder(any()) }
        }
    }

    @Test
    fun `OrderCreated should send mail when successful`() {
        val itemRepoMock =
            mockk<ItemRepository> {
                coEvery { getItems(listOf("item-id-1", "item-id-2"), HostName.AXIELL) } returns testItemList
            }

        val outboxProcessor =
            OutboxProcessor(
                outboxRepository = outboxRepoMock,
                storageSystems = emptyList(),
                itemRepository = itemRepoMock,
                emailNotifier = emailNotifierMock
            )

        runTest {
            outboxProcessor.handleEvent(OrderCreated(testOrder))
            assertThat(emailNotifierMock.orderCreatedCount).isEqualTo(1)
        }
    }

    @Test
    fun `OrderCreated Should not mark as processed if anything fails`() {
        val itemRepoMock =
            mockk<ItemRepository> {
                coEvery { getItems(listOf("item-id-1", "item-id-2"), HostName.AXIELL) } returns testItemList
            }
        val errorMessage = "Some exception when sending to storage system"
        val invalidStorageMock =
            mockk<StorageSystemFacade> {
                coEvery { canHandleLocation(any()) } returns true
                coEvery {
                    createOrder(any())
                } throws NotImplementedError(errorMessage)
            }

        val outboxProcessor =
            OutboxProcessor(
                outboxRepository = outboxRepoMock,
                storageSystems = listOf(invalidStorageMock),
                itemRepository = itemRepoMock,
                emailNotifier = emailNotifierMock
            )

        assertThatThrownBy {
            runTest {
                outboxProcessor.handleEvent(OrderCreated(testOrder))
            }
        }
        assertThat(outboxRepoMock.processed).hasSize(0)
    }

    @Test
    fun `ItemCreated should mark as processed when successful`() {
        val itemRepoMock =
            mockk<ItemRepository> {
                coEvery { getItem(HostName.AXIELL, "test-item-1") } returns null
            }

        val outboxProcessor =
            OutboxProcessor(
                outboxRepository = outboxRepoMock,
                storageSystems = listOf(happyStorageSystemMock),
                itemRepository = itemRepoMock,
                emailNotifier = emailNotifierMock
            )

        runTest {
            val event = ItemCreated(testItem)
            outboxProcessor.handleEvent(event)
            assertThat(outboxRepoMock.processed).hasSize(1).contains(event)
        }
    }

    // FIXME - When does this case (if ever) occur?
    @Test
    fun `ItemCreated should fail if item already exists`() {
        val itemRepoMock =
            mockk<ItemRepository> {
                coEvery { getItem(HostName.AXIELL, "test-item-1") } returns testItem
            }

        val storageSystemMock =
            mockk<StorageSystemFacade> {
                coEvery { canHandleItem(any()) } returns true
                coEvery { canHandleLocation("SOMEWHERE-KNOWN") } returns true
                coEvery { canHandleLocation("SOMEWHERE") } returns false
                coEvery { createItem(any()) } throws StorageSystemException("Duplicate product")
            }

        val outboxProcessor =
            OutboxProcessor(
                outboxRepository = outboxRepoMock,
                storageSystems = listOf(storageSystemMock),
                itemRepository = itemRepoMock,
                emailNotifier = emailNotifierMock
            )

        runTest {
            val event = ItemCreated(testItem)
            outboxProcessor.handleEvent(event)
            assertThat(outboxRepoMock.processed).hasSize(1).contains(event)
            assertDoesNotThrow {
                itemRepoMock.getItem(HostName.AXIELL, "test-item-1")
            }
        }
    }

    @Test
    fun `ItemCreated should succeed despite no valid locations existing`() {
        val itemRepoMock =
            mockk<ItemRepository> {
                coEvery { getItem(HostName.AXIELL, "test-item-1") } returns null
            }

        val testItem = testItem.copy(location = "SOMEWHERE")

        val outboxProcessor =
            OutboxProcessor(
                outboxRepository = outboxRepoMock,
                storageSystems = listOf(happyStorageSystemMock),
                itemRepository = itemRepoMock,
                emailNotifier = emailNotifierMock
            )

        runTest {
            val event = ItemCreated(testItem)
            outboxProcessor.handleEvent(event)
            assertThat(outboxRepoMock.processed).hasSize(1).contains(event)
        }
    }

    @Test
    fun `UpdateOrder should mark as processed if successful`() {
        val extendedTestItemList = testItemList.plus(testItem.copy(hostId = "item-id-3"))
        val itemRepoMock =
            mockk<ItemRepository> {
                coEvery { getItems(listOf("item-id-1", "item-id-2", "item-id-3"), HostName.AXIELL) } returns extendedTestItemList
                coEvery { getItems(listOf("item-id-1"), HostName.AXIELL) } returns listOf(extendedTestItemList[0])
                coEvery { getItems(listOf("item-id-2"), HostName.AXIELL) } returns listOf(extendedTestItemList[1])
                coEvery { getItems(listOf("item-id-3"), HostName.AXIELL) } returns listOf(extendedTestItemList[2])
            }

        val expectedOrder =
            testOrder.copy(
                note = "I want this soon",
                orderLine = testOrder.orderLine.plus(Order.OrderItem("item-id-3", status = Order.OrderItem.Status.NOT_STARTED))
            )

        val storageSystemMock =
            mockk<StorageSystemFacade> {
                coEvery { canHandleItem(any()) } returns true
                coEvery { canHandleLocation("SOMEWHERE-KNOWN") } returns true
                coEvery { canHandleLocation("SOMEWHERE") } returns false
                coEvery { updateOrder(expectedOrder) } returns expectedOrder
            }

        val outboxProcessor =
            OutboxProcessor(
                outboxRepository = outboxRepoMock,
                storageSystems = listOf(storageSystemMock),
                itemRepository = itemRepoMock,
                emailNotifier = emailNotifierMock
            )

        runTest {
            val event = OrderUpdated(expectedOrder)
            outboxProcessor.handleEvent(event)
            assertThat(outboxRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { storageSystemMock.updateOrder(any()) }
        }
    }

    fun `DeleteOrder should mark as processed if successful`() {
        TODO("Not implemented")
    }

    //
    // TEST OBJECTS
    //
    private val testOrder =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "some-order-id",
            status = Order.Status.NOT_STARTED,
            orderLine =
                listOf(
                    Order.OrderItem("item-id-1", Order.OrderItem.Status.NOT_STARTED),
                    Order.OrderItem("item-id-2", Order.OrderItem.Status.NOT_STARTED)
                ),
            orderType = Order.Type.LOAN,
            address = null,
            contactPerson = "Ole Nordmann",
            note = null,
            callbackUrl = "https://callback.url"
        )

    private val testItem =
        Item(
            hostName = HostName.AXIELL,
            hostId = "item-id-1",
            description = "Tyven, tyven skal du hete",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            callbackUrl = "https://callback-wls.no/item",
            location = "SOMEWHERE",
            quantity = 1
        )

    private val testItemList =
        listOf(
            Item(
                hostName = HostName.AXIELL,
                hostId = "item-id-1",
                description = "Tyven, tyven skal du hete",
                itemCategory = ItemCategory.PAPER,
                preferredEnvironment = Environment.NONE,
                packaging = Packaging.NONE,
                callbackUrl = "https://callback-wls.no/item",
                location = "SOMEWHERE",
                quantity = 1
            ),
            Item(
                hostName = HostName.AXIELL,
                hostId = "item-id-2",
                description = "Tyven, tyven skal du hete 2",
                itemCategory = ItemCategory.PAPER,
                preferredEnvironment = Environment.NONE,
                packaging = Packaging.NONE,
                callbackUrl = "https://callback-wls.no/item",
                location = "SOMEWHERE-KNOWN",
                quantity = 1
            )
        )
}
