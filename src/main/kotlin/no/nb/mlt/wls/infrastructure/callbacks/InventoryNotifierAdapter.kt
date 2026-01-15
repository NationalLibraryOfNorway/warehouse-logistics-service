package no.nb.mlt.wls.infrastructure.callbacks

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.resolver.dns.DnsErrorCauseException
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import no.nb.mlt.wls.domain.ports.outbound.exceptions.UnableToNotifyException
import no.nb.mlt.wls.infrastructure.config.TimeoutProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

@Component
class InventoryNotifierAdapter(
    @param:Qualifier("nonProxyWebClient")
    private val webClient: WebClient,
    @param:Qualifier("proxyWebClient")
    private val proxyWebClient: WebClient,
    @param:Value($$"${callback.secret}")
    private val signatureSecretKey: String,
    private val objectMapper: ObjectMapper,
    private val timeoutConfig: TimeoutProperties
) : InventoryNotifier {
    override fun itemChanged(
        item: Item,
        eventTimestamp: Instant,
        messageId: String
    ) {
        if (item.callbackUrl != null) {
            val payload = objectMapper.writeValueAsString(item.toNotificationItemPayload(eventTimestamp, messageId))
            val timestamp = System.currentTimeMillis().toString()

            sendCallback(item.hostName, item.callbackUrl, payload, timestamp)
        }
    }

    override fun orderChanged(
        order: Order,
        eventTimestamp: Instant,
        messageId: String
    ) {
        val payload = objectMapper.writeValueAsString(order.toNotificationOrderPayload(eventTimestamp, messageId))
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
            .timeout(timeoutConfig.inventory)
            .onErrorComplete { error ->
                shouldCompleteOnError(error, callbackUrl, payload)
            }.doOnError {
                logger.error(it) { "Error while sending update to callback URL: $callbackUrl" }
            }.onErrorMap { UnableToNotifyException("Unable to send callback", it) }
            .block()
    }

    /**
     * Determines if the error should complete the reactive chain (no retry) or propagate (allow retry).
     * Returns true for errors that should not be retried (malformed URLs, 4xx errors).
     * Returns false for errors that should follow normal retry flow (5xx errors, other errors).
     */
    private fun shouldCompleteOnError(
        error: Throwable,
        callbackUrl: String,
        payload: String
    ): Boolean {
        // Check for malformed callback URL
        val callbackUrlIsMalformed =
            error.cause is DnsErrorCauseException ||
                error.stackTraceToString().contains("Failed to resolve")
        if (callbackUrlIsMalformed) {
            logger.error(error) {
                "Cannot resolve callback URL: $callbackUrl, we will never retry sending this message: $payload"
            }
            return true
        }

        // Handle HTTP response errors
        if (error is WebClientResponseException) {
            // 4xx errors - client errors that should not be retried
            if (error.statusCode.is4xxClientError) {
                if (callbackUrl.contains("asta") && error.statusCode == HttpStatus.CONFLICT) return true
                logger.error(error) {
                    "Received 4xx error (${error.statusCode.value()}) from callback URL: $callbackUrl, " +
                        "we will never retry sending this message: $payload"
                }
                return true
            }

            // 5xx errors - server errors, log and continue normal retry flow
            if (error.statusCode.is5xxServerError) {
                logger.error(error) {
                    "Received 5xx error (${error.statusCode.value()}) from callback URL: $callbackUrl, " +
                        "will continue with normal retry flow"
                }
                return false
            }
        }

        // For all other errors, propagate them (don't complete)
        return false
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
