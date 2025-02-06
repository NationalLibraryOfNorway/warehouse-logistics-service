package no.nb.mlt.wls.infrastructure.callbacks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class InventoryNotifierAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var inventoryNotifierAdapter: InventoryNotifierAdapter
    private val secretKey = "your-secret-key"

    private lateinit var testItem: Item
    private lateinit var testOrder: Order

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build()
        inventoryNotifierAdapter = InventoryNotifierAdapter(webClient, secretKey, jacksonObjectMapper())
        testItem = Item(
            hostId = "item-id",
            hostName = HostName.AXIELL,
            description = "item-description",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            callbackUrl = mockWebServer.url("/item-callback").toString(),
            location = "location",
            quantity = 1
        )
        testOrder = Order(
            hostName = HostName.AXIELL,
            hostOrderId = "order-id",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(Order.OrderItem("item-id", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            address = null,
            contactPerson = "contact-person",
            note = null,
            callbackUrl = mockWebServer.url("/order-callback").toString()
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should send callback on itemChange`() {
        inventoryNotifierAdapter.itemChanged(testItem)
        val request = mockWebServer.takeRequest()
        assertEquals("/item-callback", request.path)
        assertEquals("POST", request.method)
        val requestItem: NotificationItemPayload = jacksonObjectMapper().readValue(request.body.readUtf8())
        assertEquals(NotificationItemPayload(
            hostId = testItem.hostId,
            hostName = testItem.hostName,
            description = testItem.description,
            itemCategory = testItem.itemCategory,
            preferredEnvironment = testItem.preferredEnvironment,
            packaging = testItem.packaging,
            location = testItem.location,
            quantity = testItem.quantity,
            callbackUrl = testItem.callbackUrl
        ), requestItem)
    }

    @Test
    fun `should send callback on orderChange`() {
        inventoryNotifierAdapter.orderChanged(testOrder)
        val request = mockWebServer.takeRequest()
        assertEquals("/order-callback", request.path)
        assertEquals("POST", request.method)
        val requestOrder: NotificationOrderPayload = jacksonObjectMapper().readValue(request.body.readUtf8())
        assertEquals(NotificationOrderPayload(
            hostName = testOrder.hostName,
            hostOrderId = testOrder.hostOrderId,
            status = testOrder.status,
            orderLine = testOrder.orderLine.map { NotificationOrderPayload.OrderLine(it.hostId, it.status) },
            orderType = testOrder.orderType,
            address = testOrder.address,
            contactPerson = testOrder.contactPerson,
            note = testOrder.note,
            callbackUrl = testOrder.callbackUrl
        ), requestOrder)
    }

    @Test
    fun `should include signature header in item changed callback`() {
        inventoryNotifierAdapter.itemChanged(testItem)
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
        inventoryNotifierAdapter.orderChanged(testOrder)
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
