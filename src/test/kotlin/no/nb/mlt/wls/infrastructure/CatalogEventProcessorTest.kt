package no.nb.mlt.wls.infrastructure

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.testItem
import no.nb.mlt.wls.testOrder
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cloud.gateway.support.TimeoutException
import java.time.Instant

class CatalogEventProcessorTest {


////////////////////////////////////////////////////////////////////////////////
///////////////////////////////  Test Functions  ///////////////////////////////
////////////////////////////////////////////////////////////////////////////////


    @Test
    fun `order update should call inventory notifier and mark message as processed`() {
        val messageProcessor = CatalogEventProcessorAdapter(
            catalogEventRepository = catalogMessageRepoMock, inventoryNotifier = happyInventoryNotifierMock
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

        val messageProcessor = CatalogEventProcessorAdapter(
            catalogEventRepository = catalogMessageRepoMock, inventoryNotifier = happyInventoryNotifierMock
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
        val messageProcessor = CatalogEventProcessorAdapter(
            catalogEventRepository = catalogMessageRepoMock, inventoryNotifier = happyInventoryNotifierMock
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

        val messageProcessor = CatalogEventProcessorAdapter(
            catalogEventRepository = catalogMessageRepoMock, inventoryNotifier = happyInventoryNotifierMock
        )

        runTest {
            val event = ItemEvent(testItem, eventTimestamp = Instant.now())
            assertThrows<TimeoutException> { messageProcessor.handleEvent(event) }
            assertThat(catalogMessageRepoMock.processed).hasSize(0).doesNotContain(event)
            coVerify(exactly = 1) { happyInventoryNotifierMock.itemChanged(any(), event.eventTimestamp) }
        }
    }


////////////////////////////////////////////////////////////////////////////////
////////////////////////////////  Test Helpers  ////////////////////////////////
////////////////////////////////////////////////////////////////////////////////


    private val catalogMessageRepoMock = object : EventRepository<CatalogEvent> {
        val processed: MutableList<CatalogEvent> = mutableListOf()

        override suspend fun save(event: CatalogEvent): CatalogEvent {
            TODO("Not relevant for testing")
        }

        override suspend fun getAll(): List<CatalogEvent> {
            TODO("Not relevant for testing")
        }

        override suspend fun getUnprocessedSortedByCreatedTime() = emptyList<CatalogEvent>()

        override suspend fun markAsProcessed(event: CatalogEvent): CatalogEvent {
            processed.add(event)
            return event
        }
    }

    private val happyInventoryNotifierMock = mockk<InventoryNotifier> {
        coEvery { itemChanged(any(), any()) } returns Unit
        coEvery { orderChanged(any(), any()) } returns Unit
    }
}
