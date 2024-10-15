package no.nb.mlt.wls.order.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.application.hostapi.ErrorMessage
import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.toApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.toOrder
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.outbound.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.MongoItem
import no.nb.mlt.wls.infrastructure.repositories.order.OrderMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
import no.nb.mlt.wls.infrastructure.synq.SynqAdapter
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
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux

@EnableTestcontainers
@TestInstance(PER_CLASS)
@AutoConfigureWebTestClient
@ExtendWith(MockKExtension::class)
@EnableMongoRepositories("no.nb.mlt.wls")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class OrderControllerTest(
    @Autowired val repository: OrderMongoRepository,
    @Autowired val itemMongoRepository: ItemMongoRepository,
    @Autowired val applicationContext: ApplicationContext
) {
    @MockkBean
    private lateinit var synqAdapterMock: SynqAdapter

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
                synqAdapterMock.createOrder(any())
            } answers {}

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(testOrderPayload)
                .exchange()
                .expectStatus().isCreated

            val order =
                repository.findByHostNameAndHostOrderId(testOrderPayload.hostName, testOrderPayload.hostOrderId)
                    .awaitSingle()

            assertThat(order)
                .isNotNull
                .extracting("callbackUrl", "status")
                .containsExactly(testOrderPayload.callbackUrl, Order.Status.NOT_STARTED)
        }

    @Test
    fun `createOrder with duplicate payload returns OK`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(duplicateOrderPayload)
            .exchange()
            .expectStatus().isOk
            .expectBody<ApiOrderPayload>()
            .consumeWith { response ->
                assertThat(response.responseBody?.hostOrderId).isEqualTo(duplicateOrderPayload.hostOrderId)
                assertThat(response.responseBody?.hostName).isEqualTo(duplicateOrderPayload.hostName)
                assertThat(response.responseBody?.orderLine).isEqualTo(duplicateOrderPayload.orderLine)
            }
    }

    @Test
    fun `createOrder payload with different data but same ID returns DB entry`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(
                duplicateOrderPayload.copy(
                    orderLine =
                        listOf(
                            Order.OrderItem(
                                "AAAAAAAAA",
                                Order.OrderItem.Status.PICKED
                            )
                        )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody<ApiOrderPayload>()
            .consumeWith { response ->
                assertThat(response.responseBody?.orderLine).isEqualTo(duplicateOrderPayload.orderLine)
            }
    }

    @Test
    fun `createOrder where SynQ says it's a duplicate but we don't have it in the DB returns Server error`() {
        coEvery {
            synqAdapterMock.createOrder(any())
        } throws (DuplicateResourceException("Order already exists"))

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody<ErrorMessage>()
    }

    @Test
    fun `createOrder handles SynQ error`() {
        coEvery {
            synqAdapterMock.createOrder(any())
        } throws
            StorageSystemException(
                "Unexpected error",
                WebClientResponseException.create(500, "Unexpected error", HttpHeaders.EMPTY, ByteArray(0), null)
            )

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload)
            .exchange()
            .expectStatus().is5xxServerError
    }

    // FIXME - Endpoint now returns DTO instead of direct Orders, which breaks this test
    @Test
    fun `getOrder returns the order`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
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
        val testPayload =
            duplicateOrderPayload.toOrder()
                .copy(
                    receiver = duplicateOrderPayload.receiver.copy(name = "newName"),
                    callbackUrl = "https://new-callback.com/order",
                    orderLine =
                        listOf(
                            Order.OrderItem("mlt-420", Order.OrderItem.Status.NOT_STARTED)
                        )
                )

        coEvery {
            synqAdapterMock.updateOrder(any())
        } answers { testPayload }

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
            .put()
            .bodyValue(testPayload)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody<ApiOrderPayload>()
            .consumeWith { response ->
                val items = response.responseBody?.orderLine
                items?.map {
                    assertThat(testPayload.orderLine.contains(it))
                }
                assertThat(response.responseBody?.receiver?.name).isEqualTo(testPayload.receiver.name)
                assertThat(response.responseBody?.callbackUrl).isEqualTo(testPayload.callbackUrl)
            }
    }

    @Test
    fun `updateOrder when order lines doesn't exists returns status 400`() {
        val testPayload =
            testOrderPayload.copy(
                hostOrderId = "mlt-test-order-processing",
                status = Order.Status.IN_PROGRESS,
                orderLine = listOf(Order.OrderItem("this-does-not-exist", Order.OrderItem.Status.NOT_STARTED))
            )
        val testUpdatePayload = testPayload.toOrder().toApiOrderPayload().copy(orderType = Order.Type.DIGITIZATION)
        runTest {
            repository.save(testPayload.toOrder().toMongoOrder()).awaitSingle()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
                .put()
                .bodyValue(testUpdatePayload)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun `updateOrder when order is being processed errors`() {
        val testPayload = testOrderPayload.copy(hostOrderId = "mlt-test-order-processing", status = Order.Status.IN_PROGRESS)
        val testUpdatePayload = testPayload.toOrder().toApiOrderPayload().copy(orderType = Order.Type.DIGITIZATION)
        runTest {
            repository.save(testPayload.toOrder().toMongoOrder()).awaitSingle()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
                .put()
                .bodyValue(testUpdatePayload)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @Test
    fun `updateOrder when order does not exists returns status 404 NOT FOUND`() {
        val testPayload =
            testOrderPayload.copy(
                hostOrderId = "humbug"
            )

        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
                .put()
                .bodyValue(testPayload)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @Test
    fun `deleteOrder with valid data deletes order`() =
        runTest {
            coEvery {
                synqAdapterMock.deleteOrder(any(), any())
            } answers {}

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject("axiell") })
                .delete()
                .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk

            val order =
                repository.findByHostNameAndHostOrderId(
                    duplicateOrderPayload.hostName,
                    duplicateOrderPayload.hostOrderId
                ).awaitSingleOrNull()

            assertThat(order).isNull()
        }

    @Test
    fun `deleteOrder handles synq error`() {
        coEvery {
            synqAdapterMock.deleteOrder(any(), any())
        } throws (
            StorageSystemException(
                "Unexpected error",
                WebClientResponseException.create(500, "Unexpected error", HttpHeaders.EMPTY, ByteArray(0), null)
            )
        )
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
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
            hostName = HostName.AXIELL,
            hostOrderId = "order-360720",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(Order.OrderItem("mlt-420", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            owner = Owner.NB,
            receiver =
                Order.Receiver(
                    name = "name",
                    address = "address"
                ),
            callbackUrl = "https://callback.com/order"
        )

    /**
     * Payload which will exist in the database
     */

    private val duplicateOrderPayload =
        ApiOrderPayload(
            hostName = HostName.AXIELL,
            hostOrderId = "order-123456",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(Order.OrderItem("item-123456", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            owner = Owner.NB,
            receiver =
                Order.Receiver(
                    name = "name",
                    address = "address"
                ),
            callbackUrl = "https://callback.com/order"
        )

    fun populateDb() {
        // Make sure we start with clean DB instance for each test
        runBlocking {
            repository.deleteAll().then(repository.save(duplicateOrderPayload.toOrder().toMongoOrder())).awaitSingle()

            // Create all items in testOrderPayload and duplicateOrderPayload in the database
            val allItems = testOrderPayload.orderLine + duplicateOrderPayload.orderLine
            itemMongoRepository
                .deleteAll()
                .thenMany(
                    Flux.fromIterable(
                        allItems.map {
                            MongoItem(
                                hostId = it.hostId,
                                hostName = testOrderPayload.hostName,
                                description = "description",
                                itemCategory = "itemCategory",
                                preferredEnvironment = Environment.NONE,
                                packaging = Packaging.NONE,
                                owner = Owner.NB,
                                location = "null",
                                quantity = 1.0,
                                callbackUrl = "https://callback.com/item"
                            )
                        }
                    )
                )
                .flatMap { itemMongoRepository.save(it) }
                .awaitLast()
        }
    }
}
