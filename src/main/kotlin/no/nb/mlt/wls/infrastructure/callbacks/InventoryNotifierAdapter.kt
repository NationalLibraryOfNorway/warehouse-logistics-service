package no.nb.mlt.wls.infrastructure.callbacks

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.TimeoutProperties
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

@Component
class InventoryNotifierAdapter(
    @Qualifier("nonProxyWebClient")
    private val webClient: WebClient,
    @Qualifier("proxyWebClient")
    private val proxyWebClient: WebClient,
    @Value("\${callback.secret}")
    private val signatureSecretKey: String,
    private val objectMapper: ObjectMapper,
    private val timeoutConfig: TimeoutProperties
) : InventoryNotifier {
    override fun itemChanged(
        item: Item,
        eventTimestamp: Instant
    ) {
        if (item.callbackUrl != null) {
            val payload = objectMapper.writeValueAsString(item.toNotificationItemPayload(eventTimestamp))
            val timestamp = System.currentTimeMillis().toString()

            sendCallback(item.hostName, item.callbackUrl, payload, timestamp)
        }
    }

    override fun orderChanged(
        order: Order,
        eventTimestamp: Instant
    ) {
        val payload = objectMapper.writeValueAsString(order.toNotificationOrderPayload(eventTimestamp))
        val timestamp = System.currentTimeMillis().toString()

        sendCallback(order.hostName, order.callbackUrl, payload, timestamp)
    }

    private fun sendCallback(
        hostName: HostName,
        callbackUrl: String,
        payload: String,
        timestamp: String
    ) {
        getAppropriateWebClient(hostName)
            .post()
            .uri(callbackUrl)
            .bodyValue(payload)
            .headers {
                it.contentType = MediaType.APPLICATION_JSON
                it["X-Signature"] = generateSignature(payload, timestamp)
                it["X-Timestamp"] = timestamp
            }.retrieve()
            .bodyToMono(Void::class.java)
            .timeout(timeoutConfig.inventory())
            .doOnError {
                logger.error(it) { "Error while sending update to callback URL: $callbackUrl" }
                throw it
            }.block()
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

    private fun getAppropriateWebClient(hostName: HostName): WebClient = if (hostName == HostName.ASTA) proxyWebClient else webClient
}
