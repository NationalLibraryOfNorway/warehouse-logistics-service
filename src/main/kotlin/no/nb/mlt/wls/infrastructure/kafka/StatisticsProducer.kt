package no.nb.mlt.wls.infrastructure.kafka

import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kafka.sender.SenderResult

@Service
class StatisticsProducer(
    private val kafkaTemplate: ReactiveKafkaProducerTemplate<String, Any>
) {
    @Value($$"${spring.kafka.template.default-topic}")
    private lateinit var defaultTopic: String

    fun sendStatisticsMessage(
        key: String,
        message: Any
    ): Mono<SenderResult<Void>> = sendMessage(defaultTopic, key, message)

    // Marking this as private for now since we only need to send statistics messages
    // However, if we need it in the future, we can make this public and send messages to whatever topic we want
    private fun sendMessage(
        topic: String,
        key: String,
        message: Any
    ): Mono<SenderResult<Void>> = kafkaTemplate.send(topic, key, message)
}
