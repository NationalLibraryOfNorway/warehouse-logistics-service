package no.nb.mlt.wls.infrastructure.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

@Service
class StatisticsProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    @Value($$"${spring.kafka.template.default-topic}")
    private lateinit var defaultTopic: String

    fun sendStatisticsMessage(
        key: String,
        message: Any
    ): Future<SendResult<String, Any>> = sendMessage(defaultTopic, key, message)

    // Marking this as private for now since we only need to send statistics messages
    // However, if we need it in the future, we can make this public and send messages to whatever topic we want
    private fun sendMessage(
        topic: String,
        key: String,
        message: Any
    ): CompletableFuture<SendResult<String, Any>> = kafkaTemplate.send(ProducerRecord(topic, key, message))
}
