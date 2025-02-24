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
import no.nb.mlt.wls.domain.model.outboxMessages.OrderCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OutboxMessage
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OutboxRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OutboxProcessorTest {

    private val outboxRepoMock = object: OutboxRepository {
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

    private val emailNotifierMock = object : EmailNotifier {
        var orderCreatedCount = 0;
        var orderUpdatedCount = 0;


        override suspend fun orderCreated(order: Order, orderItems: List<Item>) {
            orderCreatedCount++
        }

        override suspend fun orderUpdated(order: Order) {
            orderUpdatedCount++
        }

    }

    @Test
    fun `Should handle OrderCreated and mark as processed if everything is ok`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItems(listOf("item-id-1", "item-id-2"), HostName.AXIELL) } returns itemList
            coEvery { getItems(listOf("item-id-1"), HostName.AXIELL) } returns listOf(itemList[0])
            coEvery { getItems(listOf("item-id-2"), HostName.AXIELL) } returns listOf(itemList[1])
        }

        val storageSystemMock = mockk<StorageSystemFacade> {
            coEvery { canHandleLocation("SOMEWHERE-KNOWN") } returns true
            coEvery { canHandleLocation("SOMEWHERE") } returns false
            coEvery { createOrder(any()) } returns Unit
        }

        val outboxProcessor = OutboxProcessor(
            outboxRepository = outboxRepoMock,
            storageSystems = listOf(storageSystemMock),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        runTest {
            outboxProcessor.handleEvent(OrderCreated(order))
            assertThat(outboxRepoMock.processed).hasSize(1).contains(OrderCreated(order))
            coVerify(exactly = 1) { storageSystemMock.createOrder(any()) }
        }
    }

    @Test
    fun `Should send mail on OrderCreated`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItems(listOf("item-id-1", "item-id-2"), HostName.AXIELL) } returns itemList
        }

        val outboxProcessor = OutboxProcessor(
            outboxRepository = outboxRepoMock,
            storageSystems = emptyList(),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        runTest {
            outboxProcessor.handleEvent(OrderCreated(order))
            assertThat(emailNotifierMock.orderCreatedCount).isEqualTo(1)
        }
    }

    @Test
    fun `Should not mark as processed if anything fails`() {
        val itemRepoMock = mockk<ItemRepository> {
            coEvery { getItems(listOf("item-id-1", "item-id-2"), HostName.AXIELL) } returns itemList
        }
        val errorMessage = "Some exception when sending to storage system"
        val storageSystemMock = mockk<StorageSystemFacade> {
            coEvery { canHandleLocation(any()) } returns true
            coEvery {
                createOrder(any())
            } throws NotImplementedError(errorMessage)
        }

        val outboxProcessor = OutboxProcessor(
            outboxRepository = outboxRepoMock,
            storageSystems = listOf(storageSystemMock),
            itemRepository = itemRepoMock,
            emailNotifier = emailNotifierMock
        )

        assertThatThrownBy {
            runTest {
                outboxProcessor.handleEvent(OrderCreated(order))
            }
        }
        assertThat(outboxRepoMock.processed).hasSize(0)
    }

    private val order = Order(
        hostName = HostName.AXIELL,
        hostOrderId = "some-order-id",
        status = Order.Status.NOT_STARTED,
        orderLine = listOf(
            Order.OrderItem("item-id-1", Order.OrderItem.Status.NOT_STARTED),
            Order.OrderItem("item-id-2", Order.OrderItem.Status.NOT_STARTED)
        ),
        orderType = Order.Type.LOAN,
        address = null,
        contactPerson = "Ole Nordmann",
        note = null,
        callbackUrl = "https://callback.url"
    )

    private val itemList = listOf(
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
