package no.nb.mlt.wls.infrastructure.callbacks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.junit5.MockKExtension
import io.mockk.spyk
import io.mockk.verify
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.exceptions.UnableToNotifyException
import no.nb.mlt.wls.infrastructure.config.TimeoutProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@ExtendWith(MockKExtension::class)
class InventoryNotifierAdapterTest {
    @MockkSpyBean(name = "proxyWebClient")
    private lateinit var proxyWebClient: WebClient

    @MockkSpyBean(name = "nonProxyWebClient")
    private lateinit var webClient: WebClient

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockServerItemCallbackPath: String
    private lateinit var mockServerOrderCallbackPath: String

    private lateinit var inventoryNotifierAdapter: InventoryNotifierAdapter

    private lateinit var testItemWithCallback: Item
    private lateinit var testOrderWithCallback: Order
    private lateinit var itemNotificationPayload: NotificationItemPayload
    private lateinit var orderNotificationPayload: NotificationOrderPayload
    private val timeoutConfig = TimeoutProperties(8, 8, 8)

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        webClient = spyk(WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build())
        proxyWebClient = spyk(WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build())

        mockServerItemCallbackPath = mockWebServer.url("/item-callback").toString()
        mockServerOrderCallbackPath = mockWebServer.url("/order-callback").toString()

        testItemWithCallback = createTestItem(callbackUrl = mockServerItemCallbackPath)
        testOrderWithCallback = createTestOrder(callbackUrl = mockServerOrderCallbackPath)
        itemNotificationPayload = testItemWithCallback.toNotificationItemPayload(timestamp, messageId)
        orderNotificationPayload = testOrderWithCallback.toNotificationOrderPayload(timestamp, messageId)

        inventoryNotifierAdapter = InventoryNotifierAdapter(webClient, proxyWebClient, secretKey, jacksonObjectMapper(), timeoutConfig)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should send callback on itemChange`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        inventoryNotifierAdapter.itemChanged(testItemWithCallback, timestamp, messageId)
        val request = mockWebServer.takeRequest()

        assertEquals(itemCallbackPath, request.path)
        assertEquals("POST", request.method)
        val requestItem: NotificationItemPayload = jacksonObjectMapper().readValue(request.body.readUtf8())
        assertEquals(itemNotificationPayload, requestItem)
        verify(exactly = 0) { proxyWebClient.post() }
        verify(exactly = 1) { webClient.post() }
    }

    @Test
    fun `should send callback on orderChange`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        inventoryNotifierAdapter.orderChanged(testOrderWithCallback, timestamp, messageId)
        val request = mockWebServer.takeRequest()

        assertEquals(orderCallbackPath, request.path)
        assertEquals("POST", request.method)
        val requestOrder: NotificationOrderPayload = jacksonObjectMapper().readValue(request.body.readUtf8())
        assertEquals(orderNotificationPayload, requestOrder)
        verify(exactly = 0) { proxyWebClient.post() }
        verify(exactly = 1) { webClient.post() }
    }

    @Test
    fun `should use proxied web client when notifying of item belonging to proxied host`() {
        val item = createTestItem(hostName = HostName.ASTA, callbackUrl = mockServerItemCallbackPath)
        val timestamp = Instant.now()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        inventoryNotifierAdapter.itemChanged(item, timestamp, messageId)
        val request = mockWebServer.takeRequest()

        assertEquals(itemCallbackPath, request.path)
        assertEquals("POST", request.method)
        verify(exactly = 1) { proxyWebClient.post() }
        verify(exactly = 0) { webClient.post() }
    }

    @Test
    fun `should use proxied web client when notifying of order belonging to proxied host`() {
        val order = testOrderWithCallback.copy(hostName = HostName.ASTA)
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        inventoryNotifierAdapter.orderChanged(order, timestamp, messageId)
        val request = mockWebServer.takeRequest()

        assertEquals(orderCallbackPath, request.path)
        assertEquals("POST", request.method)
        verify(exactly = 1) { proxyWebClient.post() }
        verify(exactly = 0) { webClient.post() }
    }

    @Test
    fun `should include signature header in item changed callback`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        inventoryNotifierAdapter.itemChanged(testItemWithCallback, timestamp, messageId)
        val request = mockWebServer.takeRequest()

        val sigHeader = request.getHeader(signatureHeader)
        assertNotNull(sigHeader)
        val timestampHeader = request.getHeader(timestampHeader)
        assertNotNull(timestampHeader)
        val bodyString = request.body.readUtf8()
        val expectedSignature = getMac().doFinal("$timestampHeader.$bodyString".toByteArray())
        val signature = Base64.getEncoder().encodeToString(expectedSignature)
        assertEquals(signature, sigHeader)
    }

    @Test
    fun `should include signature header in order changed callback`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        inventoryNotifierAdapter.orderChanged(testOrderWithCallback, timestamp, messageId)
        val request = mockWebServer.takeRequest()

        val sigHeader = request.getHeader(signatureHeader)
        assertNotNull(sigHeader)
        val timestampHeader = request.getHeader(timestampHeader)
        assertNotNull(timestampHeader)
        val bodyString = request.body.readUtf8()
        val expectedSignature = getMac().doFinal("$timestampHeader.$bodyString".toByteArray())
        val signature = Base64.getEncoder().encodeToString(expectedSignature)
        assertEquals(signature, sigHeader)
    }

    @Test
    fun `should not retry on 4xx client error for item callback`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        // Should complete without throwing exception (no retry)
        assertDoesNotThrow {
            inventoryNotifierAdapter.itemChanged(testItemWithCallback, timestamp, messageId)
        }

        val request = mockWebServer.takeRequest()
        assertEquals(itemCallbackPath, request.path)
        assertEquals("POST", request.method)

        // Verify only one request was made (no retry)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `should not retry on 4xx client error for order callback`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        assertDoesNotThrow {
            inventoryNotifierAdapter.orderChanged(testOrderWithCallback, timestamp, messageId)
        }

        val request = mockWebServer.takeRequest()
        assertEquals(orderCallbackPath, request.path)
        assertEquals("POST", request.method)

        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `should throw exception on 5xx server error for item callback to allow retry`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        assertThrows<UnableToNotifyException> {
            inventoryNotifierAdapter.itemChanged(testItemWithCallback, timestamp, messageId)
        }

        val request = mockWebServer.takeRequest()
        assertEquals(itemCallbackPath, request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun `should throw exception on 5xx server error for order callback to allow retry`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(503))

        assertThrows<UnableToNotifyException> {
            inventoryNotifierAdapter.orderChanged(testOrderWithCallback, timestamp, messageId)
        }

        val request = mockWebServer.takeRequest()
        assertEquals(orderCallbackPath, request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun `should not retry on various 4xx errors`() {
        val clientErrorCodes = listOf(400, 403, 404, 409, 422, 429)

        clientErrorCodes.forEach { statusCode ->
            mockWebServer.enqueue(MockResponse().setResponseCode(statusCode))

            assertDoesNotThrow {
                inventoryNotifierAdapter.itemChanged(testItemWithCallback, timestamp, messageId)
            }

            mockWebServer.takeRequest() // Clear the request
        }
    }

    @Test
    fun `should retry on various 401 errors`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        assertThrows<UnableToNotifyException> {
            inventoryNotifierAdapter.itemChanged(testItemWithCallback, timestamp, messageId)
        }

        mockWebServer.takeRequest() // Clear the request
    }

    @Test
    fun `should propagate error on various 5xx errors`() {
        val serverErrorCodes = listOf(500, 501, 502, 503, 504)

        serverErrorCodes.forEach { statusCode ->
            mockWebServer.enqueue(MockResponse().setResponseCode(statusCode))

            assertThrows<UnableToNotifyException> {
                inventoryNotifierAdapter.itemChanged(testItemWithCallback, timestamp, messageId)
            }

            mockWebServer.takeRequest() // Clear the request
        }
    }

    @Test
    fun `should not retry on malformed callback URL for item`() {
        val itemWithBadUrl = createTestItem(callbackUrl = "https://invalid-host-that-does-not-exist-69420.com/callback")

        assertDoesNotThrow {
            inventoryNotifierAdapter.itemChanged(itemWithBadUrl, timestamp, messageId)
        }
    }

    @Test
    fun `should not retry on malformed callback URL for order`() {
        val orderWithBadUrl = testOrderWithCallback.copy(callbackUrl = "https://invalid-host-that-does-not-exist-66642.com/callback")

        assertDoesNotThrow {
            inventoryNotifierAdapter.orderChanged(orderWithBadUrl, timestamp, messageId)
        }
    }

    private val itemCallbackPath = "/item-callback"

    private val orderCallbackPath = "/order-callback"

    private val signatureHeader = "X-Signature"

    private val timestampHeader = "X-Timestamp"

    private val secretKey = "secretKey"

    private val timestamp = Instant.now()

    private val messageId = UUID.randomUUID().toString()

    private fun getMac(): Mac {
        val hmacSHA256 = "HmacSHA256"
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), hmacSHA256)
        val mac = Mac.getInstance(hmacSHA256)
        mac.init(secretKeySpec)
        return mac
    }
}
