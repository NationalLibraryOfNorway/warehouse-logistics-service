package no.nb.mlt.wls.infrastructure.callbacks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ninjasquad.springmockk.SpykBean
import com.ninjasquad.springmockk.SpykDefinition
import io.mockk.verify
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ResolvableType
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import no.nb.mlt.wls.testItem
import no.nb.mlt.wls.testOrder

class InventoryNotifierAdapterTest {


////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////  Test Setup  /////////////////////////////////
////////////////////////////////////////////////////////////////////////////////


    @SpykBean
    @Qualifier("proxyWebClient")
    private lateinit var proxyWebClient: WebClient
    @SpykBean
    @Qualifier("nonProxyWebClient")
    private lateinit var webClient: WebClient

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockServerItemCallbackPath: String
    private lateinit var mockServerOrderCallbackPath: String

    private lateinit var inventoryNotifierAdapter: InventoryNotifierAdapter

    private lateinit var testItemWithCallback: Item
    private lateinit var testOrderWithCallback: Order
    private lateinit var itemNotificationPayload: NotificationItemPayload
    private lateinit var orderNotificationPayload: NotificationOrderPayload

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        webClient =
            SpykDefinition(
                "nonProxyWebClient",
                ResolvableType.forClass(WebClient::class.java)
            ).createSpy(WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build())

        proxyWebClient =
            SpykDefinition(
                "proxyWebClient",
                ResolvableType.forClass(WebClient::class.java)
            ).createSpy(WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build())

        mockServerItemCallbackPath = mockWebServer.url("/item-callback").toString()
        mockServerOrderCallbackPath = mockWebServer.url("/order-callback").toString()

        testItemWithCallback = testItem.copy(callbackUrl = mockServerItemCallbackPath)
        testOrderWithCallback = testOrder.copy(callbackUrl = mockServerOrderCallbackPath)
        itemNotificationPayload = testItemWithCallback.toNotificationItemPayload(timestamp)
        orderNotificationPayload = testOrderWithCallback.toNotificationOrderPayload(timestamp)

        inventoryNotifierAdapter = InventoryNotifierAdapter(webClient, proxyWebClient, secretKey, jacksonObjectMapper())



    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }


////////////////////////////////////////////////////////////////////////////////
///////////////////////////////  Test Functions  ///////////////////////////////
////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `should send callback on itemChange`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        inventoryNotifierAdapter.itemChanged(testItemWithCallback, timestamp)
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

        inventoryNotifierAdapter.orderChanged(testOrderWithCallback, timestamp)
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
        val item = testItemWithCallback.copy(hostName = HostName.ASTA)
        val item = createTestItem(hostName = HostName.ASTA, callbackUrl = mockServerItemCallbackPath)
        val timestamp = Instant.now()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        inventoryNotifierAdapter.itemChanged(item, timestamp)
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

        inventoryNotifierAdapter.orderChanged(order, timestamp)
        val request = mockWebServer.takeRequest()

        assertEquals(orderCallbackPath, request.path)
        assertEquals("POST", request.method)
        verify(exactly = 1) { proxyWebClient.post() }
        verify(exactly = 0) { webClient.post() }
    }

    @Test
    fun `should include signature header in item changed callback`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        inventoryNotifierAdapter.itemChanged(testItemWithCallback, timestamp)
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

        inventoryNotifierAdapter.orderChanged(testOrderWithCallback, timestamp)
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


////////////////////////////////////////////////////////////////////////////////
////////////////////////////////  Test Helpers  ////////////////////////////////
////////////////////////////////////////////////////////////////////////////////


    private val itemCallbackPath = "/item-callback"
    private val orderCallbackPath = "/order-callback"
    private val signatureHeader = "X-Signature"
    private val timestampHeader = "X-Timestamp"
    private val secretKey = "secretKey"
    private val timestamp = Instant.now()

    private fun getMac(): Mac {
        val hmacSHA256 = "HmacSHA256"
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), hmacSHA256)
        val mac = Mac.getInstance(hmacSHA256)
        mac.init(secretKeySpec)
        return mac
    }
}
