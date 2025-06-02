package no.nb.mlt.wls.infrastructure.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.events.Event
import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.OrderUpdated
import no.nb.mlt.wls.domain.model.statistics.StatisticsEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import reactor.core.publisher.Mono
import reactor.kafka.sender.SenderResult

class KafkaStatisticsServiceTest {
    private lateinit var statisticsProducer: StatisticsProducer
    private lateinit var kafkaStatisticsService: KafkaStatisticsService
    private val mockSenderResult = mockk<SenderResult<Void>>()

    @BeforeEach
    fun setUp() {
        statisticsProducer =
            mockk {
                every { sendStatisticsMessage(any(), any()) } returns Mono.just(mockSenderResult)
            }
        kafkaStatisticsService = KafkaStatisticsService(statisticsProducer)
    }

    @Test
    fun `recordStatisticsEvent should handle ItemCreated event`() =
        runTest {
            val event = ItemCreated(testItem)
            val expected = event.toStatisticsEvent()
            val statisticsEventSlot = slot<StatisticsEvent>()

            kafkaStatisticsService.recordStatisticsEvent(event)

            verify { statisticsProducer.sendStatisticsMessage(any(), capture(statisticsEventSlot)) }
            assertThat(statisticsEventSlot.captured)
                .extracting("itemId", "eventType", "details")
                .containsExactly(expected.itemId, expected.eventType, expected.details)
        }

    @Test
    fun `recordStatisticsEvent should handle ItemEvent event`() =
        runTest {
            val event = ItemEvent(testItem)
            val expected = event.toStatisticsEvent()
            val statisticsEventSlot = slot<StatisticsEvent>()

            kafkaStatisticsService.recordStatisticsEvent(event)

            verify { statisticsProducer.sendStatisticsMessage(any(), capture(statisticsEventSlot)) }
            assertThat(statisticsEventSlot.captured)
                .extracting("itemId", "eventType", "details")
                .containsExactly(expected.itemId, expected.eventType, expected.details)
        }

    @Test
    fun `recordStatisticsEvent should handle OrderCreated event`() =
        runTest {
            val event = OrderCreated(testOrder)
            val expected = event.toStatisticsEvent()
            val statisticsEventSlot = slot<StatisticsEvent>()

            kafkaStatisticsService.recordStatisticsEvent(event)

            verify { statisticsProducer.sendStatisticsMessage(any(), capture(statisticsEventSlot)) }
            assertThat(statisticsEventSlot.captured)
                .extracting("orderId", "eventType", "details")
                .containsExactly(expected.orderId, expected.eventType, expected.details)
        }

    @Test
    fun `recordStatisticsEvent should handle OrderEvent event`() =
        runTest {
            val event = OrderEvent(testOrder)
            val expected = event.toStatisticsEvent()
            val statisticsEventSlot = slot<StatisticsEvent>()

            kafkaStatisticsService.recordStatisticsEvent(event)

            verify { statisticsProducer.sendStatisticsMessage(any(), capture(statisticsEventSlot)) }
            assertThat(statisticsEventSlot.captured)
                .extracting("orderId", "eventType", "details")
                .containsExactly(expected.orderId, expected.eventType, expected.details)
        }

    @Test
    fun `recordStatisticsEvent should handle OrderUpdated event`() =
        runTest {
            val event = OrderUpdated(testOrder)
            val expected = event.toStatisticsEvent()
            val statisticsEventSlot = slot<StatisticsEvent>()

            kafkaStatisticsService.recordStatisticsEvent(event)

            verify { statisticsProducer.sendStatisticsMessage(any(), capture(statisticsEventSlot)) }
            assertThat(statisticsEventSlot.captured)
                .extracting("orderId", "eventType", "details")
                .containsExactly(expected.orderId, expected.eventType, expected.details)
        }

    @Test
    fun `recordStatisticsEvent should handle OrderDeleted event`() =
        runTest {
            val event = OrderDeleted(testOrder.hostName, testOrder.hostOrderId)
            val expected = event.toStatisticsEvent()
            val statisticsEventSlot = slot<StatisticsEvent>()

            kafkaStatisticsService.recordStatisticsEvent(event)

            verify { statisticsProducer.sendStatisticsMessage(any(), capture(statisticsEventSlot)) }
            assertThat(statisticsEventSlot.captured)
                .extracting("orderId", "eventType", "details")
                .containsExactly(expected.orderId, expected.eventType, expected.details)
        }

    @Test
    fun `recordStatisticsEvent should handle unknown event type`() =
        runTest {
            val unknownEvent =
                object : Event {
                    override val id: String = "unknown-id"
                    override val body: Any
                        get() = TODO("Not yet implemented")
                }

            assertDoesNotThrow { kafkaStatisticsService.recordStatisticsEvent(unknownEvent) }
            verify(exactly = 0) { statisticsProducer.sendStatisticsMessage(any(), any()) }
        }

    @Test
    fun `recordStatisticsEvent should handle error from statisticsProducer`() =
        runTest {
            val event = OrderEvent(testOrder)
            val error = RuntimeException("Kafka connection error")

            every { statisticsProducer.sendStatisticsMessage(any(), any()) } returns Mono.error(error)

            assertDoesNotThrow { kafkaStatisticsService.recordStatisticsEvent(event) }
            verify { statisticsProducer.sendStatisticsMessage(any(), any()) }
        }

    private val testItem = createTestItem()
    private val testOrder = createTestOrder()
}
