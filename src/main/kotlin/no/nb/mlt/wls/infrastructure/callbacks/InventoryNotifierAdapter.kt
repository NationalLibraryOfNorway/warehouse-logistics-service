package no.nb.mlt.wls.infrastructure.callbacks

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

@Component
class InventoryNotifierAdapter(
    private val webClient: WebClient,
    @Value("\${callback.secret}")
    private val signatureSecretKey: String,
    private val objectMapper: ObjectMapper
) : InventoryNotifier {
    override fun itemChanged(item: Item) {
        val payload = objectMapper.writeValueAsString(item.toNotificationItemPayload())
        val timestamp = System.currentTimeMillis().toString()
        val signature = generateSignature(payload, timestamp)

        if (item.callbackUrl != null) {
            webClient
                .post()
                .uri(item.callbackUrl)
                .bodyValue(payload)
                .headers {
                    it.contentType = MediaType.APPLICATION_JSON
                    it.set("X-Signature", signature)
                    it.set("X-Timestamp", timestamp)
                }
                .retrieve()
                .bodyToMono(Void::class.java)
                .retry(5)
                .timeout(Duration.ofSeconds(10))
                .doOnError {
                    logger.error(it) { "Error while sending order update to callback URL: ${order.callbackUrl}" }
                }
                .subscribe()
        }
    }

    override fun orderChanged(order: Order) {
        val payload = objectMapper.writeValueAsString(order.toNotificationOrderPayload())
        val timestamp = System.currentTimeMillis().toString()
        val signature = generateSignature(payload, timestamp)

        // TODO: Should probably have a more robust retry mechanism, what if receiver is down for a while?
        webClient
            .post()
            .uri(order.callbackUrl)
            .bodyValue(payload)
            .headers {
                it.contentType = MediaType.APPLICATION_JSON
                it.set("X-Signature", signature)
                it.set("X-Timestamp", timestamp)
            }
            .retrieve()
            .bodyToMono(Void::class.java)
            .retry(5)
            .timeout(Duration.ofSeconds(10))
            .doOnError {
                logger.error(it) { "Error while sending order update to callback URL: ${order.callbackUrl}" }
            }
            .subscribe()
    }

    private fun generateSignature(
        payload: String,
        timestamp: String
    ): String {
        val hmacSHA256 = "HmacSHA256"
        val secretKeySpec = SecretKeySpec(signatureSecretKey.toByteArray(), hmacSHA256)
        val mac = Mac.getInstance(hmacSHA256)
        mac.init(secretKeySpec)

        val signatureData = "$timestamp.$payload"
        val hmacBytes = mac.doFinal(signatureData.toByteArray())
        return Base64.getEncoder().encodeToString(hmacBytes)
    }
}
