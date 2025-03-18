package no.nb.mlt.wls.infrastructure

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.catalogMessages.CatalogMessage
import no.nb.mlt.wls.domain.model.catalogMessages.ItemUpdate
import no.nb.mlt.wls.domain.model.catalogMessages.OrderUpdate
import no.nb.mlt.wls.domain.model.storageMessages.ItemCreated
import no.nb.mlt.wls.domain.model.storageMessages.OrderCreated
import no.nb.mlt.wls.domain.model.storageMessages.OrderDeleted
import no.nb.mlt.wls.domain.model.storageMessages.OrderUpdated
import no.nb.mlt.wls.domain.ports.outbound.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.CatalogMessageRepository
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cloud.gateway.support.TimeoutException
import java.time.Instant

class CatalogMessageProcessorTest {
    private val catalogMessageRepoMock =
        object : CatalogMessageRepository {
            val processed: MutableList<CatalogMessage> = mutableListOf()

            override suspend fun save(catalogMessage: CatalogMessage): CatalogMessage {
                TODO("Not yet implemented")
            }

            override suspend fun getAll(): List<CatalogMessage> {
                TODO("Not yet implemented")
            }

            override suspend fun getUnprocessedSortedByCreatedTime() = emptyList<CatalogMessage>()

            override suspend fun markAsProcessed(catalogMessage: CatalogMessage): CatalogMessage {
                processed.add(catalogMessage)
                return catalogMessage
            }
        }

    private val happyInventoryNotifierMock =
        mockk<InventoryNotifier> {
            coEvery { itemChanged(any()) } returns Unit
            coEvery { orderChanged(any()) } returns Unit
        }

    @Test
    fun `order update should call inventory notifier and mark message as processed`() {
        val messageProcessor = CatalogMessageProcessorAdapter(
            catalogMessageRepository = catalogMessageRepoMock,
            inventoryNotifier = happyInventoryNotifierMock
        )

        runTest {
            val event = OrderUpdate(testOrder, messageTimestamp =  Instant.now())
            messageProcessor.handleEvent(event)
            assertThat(catalogMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(any()) }
        }
    }

    @Test
    fun `order update should handle error from inventory notifier`() {
        coEvery { happyInventoryNotifierMock.orderChanged(any()) } throws TimeoutException("Timed out")

        val messageProcessor = CatalogMessageProcessorAdapter(
            catalogMessageRepository = catalogMessageRepoMock,
            inventoryNotifier = happyInventoryNotifierMock
        )

        runTest {
            val event = OrderUpdate(testOrder, messageTimestamp =  Instant.now())
            assertThrows<TimeoutException> { messageProcessor.handleEvent(event) }
            assertThat(catalogMessageRepoMock.processed).hasSize(0).doesNotContain(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.orderChanged(any()) }
        }
    }

    @Test
    fun `item update should call inventory notifier and mark message as processed`() {
        val messageProcessor = CatalogMessageProcessorAdapter(
            catalogMessageRepository = catalogMessageRepoMock,
            inventoryNotifier = happyInventoryNotifierMock
        )

        runTest {
            val event = ItemUpdate(testItem, messageTimestamp =  Instant.now())
            messageProcessor.handleEvent(event)
            assertThat(catalogMessageRepoMock.processed).hasSize(1).contains(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(any()) }
        }
    }

    @Test
    fun `item update should handle error from inventory notifier`() {
        coEvery { happyInventoryNotifierMock.itemChanged(any()) } throws TimeoutException("Timed out")

        val messageProcessor = CatalogMessageProcessorAdapter(
            catalogMessageRepository = catalogMessageRepoMock,
            inventoryNotifier = happyInventoryNotifierMock
        )

        runTest {
            val event = ItemUpdate(testItem, messageTimestamp =  Instant.now())
            assertThrows<TimeoutException> { messageProcessor.handleEvent(event) }
            assertThat(catalogMessageRepoMock.processed).hasSize(0).doesNotContain(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(any()) }
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
