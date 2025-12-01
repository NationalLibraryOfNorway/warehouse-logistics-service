package no.nb.mlt.wls.infrastructure.kafka

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.util.ReflectionTestUtils
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.test.StepVerifier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class StatisticsProducerTest {
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    private lateinit var statisticsProducer: StatisticsProducer

    @BeforeEach
    fun setUp() {
        kafkaTemplate = mockk<KafkaTemplate<String, Any>>()
        statisticsProducer = StatisticsProducer(kafkaTemplate)
        // Set the defaultTopic using reflection since it's normally injected by Spring
        ReflectionTestUtils.setField(statisticsProducer, "defaultTopic", "test-topic")
    }

    @Test
    fun `Send should publish given statistics message to the default topic`() {
        // Arrange
        val key = "test-key"
        val message = mapOf("field" to "value")
        val mockResult = mockk<CompletableFuture<SendResult<String, Any>>>()

        every { kafkaTemplate.send("test-topic", key, message) } returns mockResult

        // Act & Assert
        StepVerifier
            .create(statisticsProducer.sendStatisticsMessage(key, message).toMono())
            .expectNext(mockResult)
            .verifyComplete()

        verify(exactly = 1) { kafkaTemplate.send("test-topic", key, message) }
        confirmVerified(kafkaTemplate)
    }
}
