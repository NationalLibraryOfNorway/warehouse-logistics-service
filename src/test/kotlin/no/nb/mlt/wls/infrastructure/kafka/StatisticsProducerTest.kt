package no.nb.mlt.wls.infrastructure.kafka

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.test.util.ReflectionTestUtils
import reactor.core.publisher.Mono
import reactor.kafka.sender.SenderResult
import reactor.test.StepVerifier

class StatisticsProducerTest {
    private lateinit var kafkaTemplate: ReactiveKafkaProducerTemplate<String, Any>
    private lateinit var statisticsProducer: StatisticsProducer

    @BeforeEach
    fun setUp() {
        kafkaTemplate = mockk<ReactiveKafkaProducerTemplate<String, Any>>()
        statisticsProducer = StatisticsProducer(kafkaTemplate)
        // Set the defaultTopic using reflection since it's normally injected by Spring
        ReflectionTestUtils.setField(statisticsProducer, "defaultTopic", "test-topic")
    }

    @Test
    fun `Send should publish given statistics message to the default topic`() {
        // Arrange
        val key = "test-key"
        val message = mapOf("field" to "value")
        val mockResult = mockk<SenderResult<Void>>()

        every { kafkaTemplate.send("test-topic", key, message) } returns Mono.just(mockResult)

        // Act & Assert
        StepVerifier
            .create(statisticsProducer.sendStatisticsMessage(key, message))
            .expectNext(mockResult)
            .verifyComplete()

        verify(exactly = 1) { kafkaTemplate.send("test-topic", key, message) }
        confirmVerified(kafkaTemplate)
    }
}
