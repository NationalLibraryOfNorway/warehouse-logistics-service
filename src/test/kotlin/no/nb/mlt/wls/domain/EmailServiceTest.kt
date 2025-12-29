package no.nb.mlt.wls.domain

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmailServiceTest {
    private val emailEventRepository = mockk<EventRepository<EmailEvent>>()
    private val emailEventProcessor = mockk<EventProcessor<EmailEvent>>()
    private val transactionPortExecutor =
        object : TransactionPort {
            override suspend fun <T> executeInTransaction(action: suspend () -> T): T = action()
        }
    private lateinit var cut: EmailService

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
        cut = EmailService(emailEventRepository, emailEventProcessor, transactionPortExecutor)
        coEvery { emailEventRepository.save(any()) } answers { firstArg() }
        coEvery { emailEventProcessor.handleEvent(any()) } answers {}
    }

    @Test
    fun `creating emails succeed`() {
        runTest {
            cut.createOrderPickup(testOrder, testOrderItems)
            cut.createOrderConfirmation(testOrder)
            cut.createOrderCancellation(testOrder)
            cut.createOrderCompletion(testOrder)

            coVerify(exactly = 4) { emailEventProcessor.handleEvent(any()) }
        }
    }

    val testOrderItems = listOf(
        createTestItem(hostId = "test-01"),
        createTestItem(hostId = "test-02")
    )
    val testOrder = createTestOrder(orderLine = testOrderItems.map { Order.OrderItem(it.hostId, Order.OrderItem.Status.NOT_STARTED) })
}
