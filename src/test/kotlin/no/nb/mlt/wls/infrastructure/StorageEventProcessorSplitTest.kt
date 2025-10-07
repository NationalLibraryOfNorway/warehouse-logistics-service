package no.nb.mlt.wls.infrastructure

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.StatisticsService
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StorageEventProcessorSplitTest {
    private lateinit var cut: StorageEventProcessorAdapter

    @BeforeEach
    fun beforeEach() {
        cut =
            StorageEventProcessorAdapter(
                storageEventRepository = storageMessageRepoMock,
                storageSystems = listOf(synqStorageSystemFacadeMock, kardexStorageSystemFacadeMock),
                itemRepository = itemRepoMock,
                emailNotifier = emailNotifierMock,
                happyStatisticsServiceMock
            )
    }

    @Test
    fun `CreateOrder splits orders correctly`() {
        runTest {
            val event = OrderCreated(testOrder)

            cut.handleEvent(event)

            assertThat(storageMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { synqStorageSystemFacadeMock.createOrder(any()) }
            coVerify(exactly = 1) { kardexStorageSystemFacadeMock.createOrder(any()) }
        }
    }

    private val testItem1 = createTestItem(hostId = "testItem-01", associatedStorage = AssociatedStorage.SYNQ)
    private val testItem2 = createTestItem(hostId = "testItem-02", associatedStorage = AssociatedStorage.KARDEX)

    private val testItemListSmall = listOf(testItem1, testItem2)

    private val testOrder =
        createTestOrder(
            orderLine =
                listOf(
                    Order.OrderItem(testItem1.hostId, Order.OrderItem.Status.NOT_STARTED),
                    Order.OrderItem(testItem2.hostId, Order.OrderItem.Status.NOT_STARTED)
                )
        )

    private val unprocessedEventsList = mutableListOf<StorageEvent>()

    private val storageMessageRepoMock =
        object : EventRepository<StorageEvent> {
            val processed: MutableList<StorageEvent> = mutableListOf()

            override suspend fun save(event: StorageEvent): StorageEvent {
                TODO("Not relevant for testing")
            }

            override suspend fun getAll(): List<StorageEvent> = processed

            override suspend fun getUnprocessedSortedByCreatedTime() = unprocessedEventsList

            override suspend fun markAsProcessed(event: StorageEvent): StorageEvent {
                processed.add(event)
                return event
            }
        }

    // A happy little storage system mock, he lives right here...
    private val synqStorageSystemFacadeMock =
        mockk<StorageSystemFacade> {
            coEvery { canHandleItem(any()) } returns true
            coEvery { isInStorage(AssociatedStorage.KARDEX) } returns false
            coEvery { isInStorage(AssociatedStorage.SYNQ) } returns true
            coEvery { deleteOrder(any(), any()) } returns Unit
            coEvery { createOrder(any()) } returns Unit
            coEvery { createItem(any()) } returns Unit
        }

    // And his sister, which lives next door...
    private val kardexStorageSystemFacadeMock =
        mockk<StorageSystemFacade> {
            coEvery { canHandleItem(any()) } returns true
            coEvery { isInStorage(AssociatedStorage.SYNQ) } returns false
            coEvery { isInStorage(AssociatedStorage.KARDEX) } returns true
            coEvery { deleteOrder(any(), any()) } returns Unit
            coEvery { createOrder(any()) } returns Unit
            coEvery { createItem(any()) } returns Unit
        }

    // ... and she's got a friend, a big old email notifier mock...
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
            coEvery { getItemsByIds(testItem1.hostName, listOf(testItem1.hostId, testItem2.hostId)) } returns testItemListSmall
            coEvery { getItem(testItem1.hostName, testItem1.hostId) } returns testItem1
            coEvery { getItem(testItem2.hostName, testItem2.hostId) } returns testItem2
        }

    private val happyStatisticsServiceMock =
        mockk<StatisticsService> {
            coEvery { recordStatisticsEvent(any()) } returns Unit
        }
}
