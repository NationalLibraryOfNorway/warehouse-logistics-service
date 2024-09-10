package no.nb.mlt.wls.order.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.infrastructure.synq.SynqError
import no.nb.mlt.wls.order.model.Order
import no.nb.mlt.wls.order.model.OrderLineStatus
import no.nb.mlt.wls.order.model.OrderReceiver
import no.nb.mlt.wls.order.model.OrderStatus
import no.nb.mlt.wls.order.model.OrderType
import no.nb.mlt.wls.order.model.ProductLine
import no.nb.mlt.wls.application.restapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.restapi.order.toOrder
import no.nb.mlt.wls.application.restapi.order.toUpdateOrderPayload
import no.nb.mlt.wls.order.repository.OrderRepository
import no.nb.mlt.wls.order.service.SynqOrderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.net.URI

@EnableTestcontainers
@TestInstance(PER_CLASS)
@AutoConfigureWebTestClient
@ExtendWith(MockKExtension::class)
@EnableMongoRepositories("no.nb.mlt.wls")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class MongoOrderControllerTest(
    @Autowired val repository: OrderRepository,
    @Autowired val applicationContext: ApplicationContext
) {
    @MockkBean
    private lateinit var synqOrderService: SynqOrderService

    private lateinit var webTestClient: WebTestClient

    val client: String = HostName.AXIELL.name

    @BeforeEach
    fun setUp() {
        webTestClient =
            WebTestClient
                .bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .configureClient()
                .baseUrl("/v1/order")
                .build()

        populateDb()
    }

    @Test
    fun `createOrder with valid payload creates order`() =
        runTest {
            coEvery {
                synqOrderService.createOrder(any())
            } returns ResponseEntity.created(URI.create("")).build()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) })
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(testOrderPayload)
                .exchange()
                .expectStatus().isCreated

            val order = repository.findByHostNameAndHostOrderId(testOrderPayload.hostName, testOrderPayload.hostOrderId).awaitSingle()

            assertThat(order)
                .isNotNull
                .extracting("callbackUrl", "status")
                .containsExactly("callbackUrl", OrderStatus.NOT_STARTED)
        }

    @Test
    fun `createOrder with duplicate payload returns OK`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) })
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(duplicateOrderPayload)
            .exchange()
            .expectStatus().isOk
            .expectBody<ApiOrderPayload>()
            .consumeWith { response ->
                assertThat(response.responseBody?.hostOrderId).isEqualTo(duplicateOrderPayload.hostOrderId)
                assertThat(response.responseBody?.hostName).isEqualTo(duplicateOrderPayload.hostName)
                assertThat(response.responseBody?.productLine).isEqualTo(duplicateOrderPayload.productLine)
            }
    }

    @Test
    fun `createOrder payload with different data but same ID returns DB entry`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) })
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(
                duplicateOrderPayload.copy(productLine = listOf(ProductLine("AAAAAAAAA", OrderLineStatus.PICKED)))
            )
            .exchange()
            .expectStatus().isOk
            .expectBody<ApiOrderPayload>()
            .consumeWith { response ->
                assertThat(response.responseBody?.productLine).isEqualTo(duplicateOrderPayload.productLine)
            }
    }

    @Test
    fun `createOrder where SynQ says it's a duplicate returns OK`() { // SynqService converts an error to return OK if it finds a duplicate product
        coEvery {
            synqOrderService.createOrder(any())
        } returns ResponseEntity.ok().build()

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) })
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload)
            .exchange()
            .expectStatus().isOk
            .expectBody().isEmpty
    }

    @Test
    fun `createOrder handles SynQ error`() {
        coEvery {
            synqOrderService.createOrder(any())
        } returns ResponseEntity.internalServerError().body(SynqError(9001, "Unexpected error"))

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) })
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload)
            .exchange()
            .expectStatus().is5xxServerError
    }

    // FIXME - Endpoint should be returning DTO instead of direct Orders
    @Test
    fun `getOrder returns the order`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) })
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody(Order::class.java)
            .consumeWith { response ->
                assertThat(response?.responseBody?.hostOrderId.equals(duplicateOrderPayload.hostOrderId))
                assertThat(response?.responseBody?.status?.equals(duplicateOrderPayload.status))
            }
    }

    @Test
    fun `getOrder for wrong client throws`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject("ALMA") })
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `updateOrder with valid payload updates order`() {
        coEvery {
            synqOrderService.updateOrder(any())
        } returns ResponseEntity.ok().build()

        val testPayload =
            duplicateOrderPayload.toOrder().toUpdateOrderPayload()
                .copy(
                    productLine =
                        listOf(
                            ProductLine("mlt-420", OrderLineStatus.NOT_STARTED),
                            ProductLine("mlt-421", OrderLineStatus.NOT_STARTED)
                        )
                )

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) })
            .put()
            .bodyValue(testPayload)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody<ApiOrderPayload>()
            .consumeWith { response ->
                val products = response.responseBody?.productLine
                products?.map {
                    assertThat(testPayload.productLine.contains(it))
                }
            }
    }

    @Test
    fun `updateOrder when order is being processed errors`() {
        val testPayload = testOrderPayload.copy(orderId = "mlt-test-order-processing", status = OrderStatus.IN_PROGRESS)
        val testUpdatePayload = testPayload.toOrder().toUpdateOrderPayload().copy(orderType = OrderType.DIGITIZATION)
        runTest {
            repository.save(testPayload.toOrder()).awaitSingle()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) })
                .put()
                .bodyValue(testUpdatePayload)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @Test
    fun `deleteOrder with valid data deletes order`() =
        runTest {
            coEvery {
                synqOrderService.deleteOrder(any(), any())
            } returns ResponseEntity.ok().build()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject("axiell") })
                .delete()
                .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk

            val order = repository.findByHostNameAndHostOrderId(duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId).awaitSingleOrNull()

            assertThat(order).isNull()
        }

    @Test
    fun `deleteOrder handles synq error`() {
        coEvery {
            synqOrderService.deleteOrder(any(), any())
        } returns ResponseEntity.internalServerError().build()
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) })
            .delete()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .exchange()
            .expectStatus().is5xxServerError
        assertThat(true)
    }

// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// Test Help //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    /**
     * Payload which is used in most tests
     */
    private val testOrderPayload =
        ApiOrderPayload(
            orderId = "axiell-order-69",
            hostName = HostName.AXIELL,
            hostOrderId = "axiell-order-69",
            status = OrderStatus.NOT_STARTED,
            productLine = listOf(ProductLine("mlt-420", OrderLineStatus.NOT_STARTED)),
            orderType = OrderType.LOAN,
            owner = Owner.NB,
            receiver =
                OrderReceiver(
                    name = "name",
                    address = "address",
                    postalCode = "postalCode",
                    city = "city",
                    phoneNumber = "phoneNumber",
                    location = "location"
                ),
            callbackUrl = "callbackUrl"
        )

    /**
     * Payload which will exist in the database
     */
    private val duplicateOrderPayload =
        ApiOrderPayload(
            orderId = "order-123456",
            hostName = HostName.AXIELL,
            hostOrderId = "order-123456",
            status = OrderStatus.NOT_STARTED,
            productLine = listOf(ProductLine("product-123456", OrderLineStatus.NOT_STARTED)),
            orderType = OrderType.LOAN,
            owner = Owner.NB,
            receiver =
                OrderReceiver(
                    name = "name",
                    address = "address",
                    postalCode = "postalCode",
                    city = "city",
                    phoneNumber = "phoneNumber",
                    location = "location"
                ),
            callbackUrl = "callbackUrl"
        )

    fun populateDb() {
        // Make sure we start with clean DB instance for each test
        runBlocking {
            repository.deleteAll().then(repository.save(duplicateOrderPayload.toOrder())).awaitSingle()
        }
    }
}
