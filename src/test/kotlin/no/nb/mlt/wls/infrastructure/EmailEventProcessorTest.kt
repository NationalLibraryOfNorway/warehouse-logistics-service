package no.nb.mlt.wls.infrastructure

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import no.nb.mlt.wls.domain.model.events.email.OrderConfirmationMail
import no.nb.mlt.wls.domain.model.events.email.OrderPickupMail
import no.nb.mlt.wls.domain.model.events.email.createOrderPickupData
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.UserNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmailEventProcessorTest {
    private lateinit var cut: EmailEventProcessorAdapter

    @BeforeEach
    fun beforeEach() {
        cut = EmailEventProcessorAdapter(emailEventRepositoryMock, happyUserNotifierMock)
    }

    @Test
    fun `OrderPickupMail event should call user notifier and be marked as processed`() {
        runTest {
            val event = OrderPickupMail(createOrderPickupData(order, orderItems))
            cut.handleEvent(event)
            assertThat(emailEventRepositoryMock.processed).hasSize(1).contains(event)
        }
    }

    @Test
    fun `OrderConfirmationMail event should call user notifier and be marked as processed`() {
        runTest {
            val event = OrderConfirmationMail(order)
            cut.handleEvent(event)
            assertThat(emailEventRepositoryMock.processed).hasSize(1).contains(event)
        }
    }

    private val unprocessedEventsList = mutableListOf<EmailEvent>()

    private val emailEventRepositoryMock =
        object : EventRepository<EmailEvent> {
            val processed: MutableList<EmailEvent> = mutableListOf()

            override suspend fun save(event: EmailEvent): EmailEvent {
                TODO("Not relevant for testing")
            }

            override suspend fun getAll(): List<EmailEvent> {
                TODO("Not relevant for testing")
            }

            override suspend fun getUnprocessedSortedByCreatedTime() = unprocessedEventsList

            override suspend fun markAsProcessed(event: EmailEvent): EmailEvent {
                processed.add(event)
                return event
            }
        }

    private val happyUserNotifierMock: UserNotifier =
        mockk {
            coEvery { orderPickup(any()) } answers { true }
            coEvery { orderConfirmation(any()) } answers { true }
            coEvery { orderCompleted(any()) } answers { true }
        }

    private val order = createTestOrder()

    private val orderItems = listOf(createTestItem())
}
