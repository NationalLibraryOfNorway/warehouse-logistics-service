package no.nb.mlt.wls.infrastructure.callbacks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ninjasquad.springmockk.SpykBean
import com.ninjasquad.springmockk.SpykDefinition
import io.mockk.verify
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
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

class InventoryNotifierAdapterTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var inventoryNotifierAdapter: InventoryNotifierAdapter

    @SpykBean
    @Qualifier("proxyWebClient")
    private lateinit var proxyWebClient: WebClient

    @SpykBean
    @Qualifier("nonProxyWebClient")
    private lateinit var webClient: WebClient
    private val secretKey = "your-secret-key"

    private lateinit var testItem: Item
    private lateinit var testOrder: Order
    private lateinit var mockServerItemCallbackPath: String
    private lateinit var mockServerOrderCallbackPath: String

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
        inventoryNotifierAdapter = InventoryNotifierAdapter(webClient, proxyWebClient, secretKey, jacksonObjectMapper())

        mockServerItemCallbackPath = mockWebServer.url("/item-callback").toString()
        testItem =
            Item(
                hostId = "item-id",
                hostName = HostName.AXIELL,
                description = "item-description",
                itemCategory = ItemCategory.PAPER,
                preferredEnvironment = Environment.NONE,
                packaging = Packaging.NONE,
                callbackUrl = mockServerItemCallbackPath,
                location = "location",
                quantity = 1
            )

        mockServerOrderCallbackPath = mockWebServer.url("/order-callback").toString()
        testOrder =
            Order(
                hostName = HostName.AXIELL,
                hostOrderId = "order-id",
                status = Order.Status.NOT_STARTED,
                orderLine = listOf(Order.OrderItem("item-id", Order.OrderItem.Status.NOT_STARTED)),
                orderType = Order.Type.LOAN,
                address = null,
                contactPerson = "contactPerson",
                contactEmail = "contact@ema.il",
                note = null,
                callbackUrl = mockServerOrderCallbackPath
            )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should send callback on itemChange`() {
        val timestamp = Instant.now()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        inventoryNotifierAdapter.itemChanged(testItem, timestamp)
        val request = mockWebServer.takeRequest()
        assertEquals("/item-callback", request.path)
        assertEquals("POST", request.method)
        val requestItem: NotificationItemPayload = jacksonObjectMapper().readValue(request.body.readUtf8())
        assertEquals(
            NotificationItemPayload(
                hostId = testItem.hostId,
                hostName = testItem.hostName,
                description = testItem.description,
                itemCategory = testItem.itemCategory,
                preferredEnvironment = testItem.preferredEnvironment,
                packaging = testItem.packaging,
                location = testItem.location,
                quantity = testItem.quantity,
                callbackUrl = testItem.callbackUrl,
                eventTimestamp = timestamp
            ),
            requestItem
        )
        verify(exactly = 0) { proxyWebClient.post() }
        verify(exactly = 1) { webClient.post() }
    }

    @Test
    fun `should send callback on orderChange`() {
        val timestamp = Instant.now()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        inventoryNotifierAdapter.orderChanged(testOrder, timestamp)
        val request = mockWebServer.takeRequest()
        assertEquals("/order-callback", request.path)
        assertEquals("POST", request.method)
        val requestOrder: NotificationOrderPayload = jacksonObjectMapper().readValue(request.body.readUtf8())
        assertEquals(
            NotificationOrderPayload(
                hostName = testOrder.hostName,
                hostOrderId = testOrder.hostOrderId,
                status = testOrder.status,
                orderLine = testOrder.orderLine.map { NotificationOrderPayload.OrderLine(it.hostId, it.status) },
                orderType = testOrder.orderType,
                address = testOrder.address,
                contactPerson = testOrder.contactPerson,
                contactEmail = testOrder.contactEmail,
                note = testOrder.note,
                callbackUrl = testOrder.callbackUrl,
                eventTimestamp = timestamp
            ),
            requestOrder
        )
        verify(exactly = 0) { proxyWebClient.post() }
        verify(exactly = 1) { webClient.post() }
    }

    @Test
    fun `should use proxied web client when notifying of item belonging to proxied host`() {
        val item = createTestItem(hostName = HostName.ASTA, callbackUrl = mockServerItemCallbackPath)
        val timestamp = Instant.now()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        inventoryNotifierAdapter.itemChanged(item, timestamp)
        val request = mockWebServer.takeRequest()
        assertEquals("/item-callback", request.path)
        assertEquals("POST", request.method)
        verify(exactly = 1) { proxyWebClient.post() }
        verify(exactly = 0) { webClient.post() }
    }

    @Test
    fun `should use proxied web client when notifying of order belonging to proxied host`() {
        val order = testOrder.copy(hostName = HostName.ASTA)
        val timestamp = Instant.now()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        inventoryNotifierAdapter.orderChanged(order, timestamp)
        val request = mockWebServer.takeRequest()
        assertEquals("/order-callback", request.path)
        assertEquals("POST", request.method)
        verify(exactly = 1) { proxyWebClient.post() }
        verify(exactly = 0) { webClient.post() }
    }

    @Test
    fun `should include signature header in item changed callback`() {
        val timestamp = Instant.now()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        inventoryNotifierAdapter.itemChanged(testItem, timestamp)
        val request = mockWebServer.takeRequest()
        val sigHeader = request.getHeader("X-Signature")
        assertNotNull(sigHeader)
        val timestampHeader = request.getHeader("X-Timestamp")
        assertNotNull(timestampHeader)
        val bodyString = request.body.readUtf8()
        val expectedSignature = getMac().doFinal("$timestampHeader.$bodyString".toByteArray())
        val signature = Base64.getEncoder().encodeToString(expectedSignature)
        assertEquals(signature, sigHeader)
    }

    @Test
    fun `should include signature header in order changed callback`() {
        val timestamp = Instant.now()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        inventoryNotifierAdapter.orderChanged(testOrder, timestamp)
        val request = mockWebServer.takeRequest()
        val sigHeader = request.getHeader("X-Signature")
        assertNotNull(sigHeader)
        val timestampHeader = request.getHeader("X-Timestamp")
        assertNotNull(timestampHeader)
        val bodyString = request.body.readUtf8()
        val expectedSignature = getMac().doFinal("$timestampHeader.$bodyString".toByteArray())
        val signature = Base64.getEncoder().encodeToString(expectedSignature)
        assertEquals(signature, sigHeader)
    }

    private fun getMac(): Mac {
        val hmacSHA256 = "HmacSHA256"
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), hmacSHA256)
        val mac = Mac.getInstance(hmacSHA256)
        mac.init(secretKeySpec)
        return mac
    }
}
