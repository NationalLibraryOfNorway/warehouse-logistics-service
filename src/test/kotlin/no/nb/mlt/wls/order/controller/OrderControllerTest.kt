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
import no.nb.mlt.wls.application.hostapi.order.OrderLine
import no.nb.mlt.wls.application.hostapi.order.toApiOrderPayload
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
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
    @Autowired val itemMongoRepository: ItemMongoRepository,
    @Autowired val applicationContext: ApplicationContext,
    @Autowired val repository: OrderMongoRepository
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
                    orderLine = listOf(OrderLine("AAAAAAAAA", Order.OrderItem.Status.PICKED))
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
    fun `createOrder with invalid fields returns 400`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload.copy(hostOrderId = ""))
            .exchange()
            .expectStatus().isBadRequest
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
            .expectBody(ApiOrderPayload::class.java)
            .consumeWith { response ->
                assertThat(response?.responseBody?.hostOrderId.equals(duplicateOrderPayload.hostOrderId))
                assertThat(response?.responseBody?.status?.equals(duplicateOrderPayload.status))
            }
    }

    @Test
    fun `getOrder when order doesn't exist returns 404`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, "not-an-id")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    @EnabledIfSystemProperty(
        named = "spring.profiles.active",
        matches = "local-dev",
        disabledReason = "Only local-dev has properly configured keycloak & JWT"
    )
    fun `getOrder for wrong client returns 403`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject("Alma") }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .exchange()
            .expectStatus().isForbidden

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-item")))
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `updateOrder with valid payload updates order`() {
        val testPayload =
            duplicateOrderPayload.copy(
                contactPerson = "new person",
                callbackUrl = "https://new-callback.com/order",
                orderLine = listOf(OrderLine("item-123", null))
            )

        coEvery {
            synqAdapterMock.updateOrder(any())
        } answers { testPayload.toOrder() }

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
                val orderLines = response.responseBody?.orderLine
                orderLines?.map { it ->
                    assertThat(testPayload.orderLine.contains(OrderLine(it.hostId, Order.OrderItem.Status.NOT_STARTED)))
                }
                assertThat(response.responseBody?.contactPerson).isEqualTo(testPayload.contactPerson)
                assertThat(response.responseBody?.callbackUrl).isEqualTo(testPayload.callbackUrl)
            }
    }

    @Test
    fun `updateOrder when order lines doesn't exists returns status 400`() {
        val testPayload =
            testOrderPayload.copy(
                hostOrderId = "mlt-test-order-processing",
                status = Order.Status.IN_PROGRESS,
                orderLine = listOf(OrderLine("this-does-not-exist", null))
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
    fun `updateOrder with invalid fields returns 400`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
            .put()
            .bodyValue(testOrderPayload.copy(hostOrderId = ""))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isBadRequest
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
                synqAdapterMock.deleteOrder(any())
            } answers {}

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
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
    fun `deleteOrder when order is in progress returns 409`() {
        runTest {
            // Set up the DB with the order in progress
            repository
                .save(orderInProgress.toMongoOrder())
                .awaitSingle()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
                .delete()
                .uri("/{hostName}/{hostOrderId}", orderInProgress.hostName, orderInProgress.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @Test
    fun `deleteOrder with blank hostOrderId returns 400`() =
        runTest {
            coEvery {
                synqAdapterMock.deleteOrder(any())
            } answers {}

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
                .delete()
                .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, " ")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest
        }

    @Test
    fun `deleteOrder with order that does not exist returns 404`() =
        runTest {
            coEvery {
                synqAdapterMock.deleteOrder(any())
            } answers {}

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("SCOPE_wls-order")))
                .delete()
                .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, "does-not-exist")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound
        }

    @Test
    fun `deleteOrder handles synq error`() {
        coEvery {
            synqAdapterMock.deleteOrder(any())
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
            hostOrderId = "order-123",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(OrderLine("item-123", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            address =
                Order.Address(
                    recipient = "recipient",
                    addressLine1 = "addressLine1",
                    addressLine2 = "addressLine2",
                    postcode = "postcode",
                    city = "city",
                    region = "region",
                    country = "country"
                ),
            contactPerson = "named person",
            note = "note",
            callbackUrl = "https://callback.com/order"
        )

    /**
     * Payload which will exist in the database
     */

    private val duplicateOrderPayload =
        ApiOrderPayload(
            hostName = HostName.AXIELL,
            hostOrderId = "order-456",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(OrderLine("item-456", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            address =
                Order.Address(
                    recipient = "recipient",
                    addressLine1 = "addressLine1",
                    addressLine2 = "addressLine2",
                    postcode = "postcode",
                    city = "city",
                    region = "region",
                    country = "country"
                ),
            contactPerson = "named person",
            note = "note",
            callbackUrl = "https://callback.com/order"
        )

    private val orderInProgress =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "order-in-progress",
            status = Order.Status.IN_PROGRESS,
            orderLine = listOf(Order.OrderItem("item-123", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            owner = Owner.NB,
            address =
                Order.Address(
                    recipient = "recipient",
                    addressLine1 = "addressLine1",
                    addressLine2 = "addressLine2",
                    postcode = "postcode",
                    city = "city",
                    region = "region",
                    country = "country"
                ),
            contactPerson = "named person",
            note = "note",
            callbackUrl = "https://callback.com/order"
        )

    /**
     * Populate the database with items and orders for testing
     */

    fun populateDb() {
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
                                itemCategory = ItemCategory.PAPER,
                                preferredEnvironment = Environment.NONE,
                                packaging = Packaging.NONE,
                                owner = Owner.NB,
                                location = "location",
                                quantity = 1,
                                callbackUrl = "https://callback.com/item"
                            )
                        }
                    )
                )
                .flatMap { itemMongoRepository.save(it) }
                .awaitLast()
        }
    }

    private fun ApiOrderPayload.toOrder() =
        Order(
            hostName = hostName,
            hostOrderId = hostOrderId,
            status = status ?: Order.Status.NOT_STARTED,
            orderLine = orderLine.map { it.toOrderItem() },
            orderType = orderType,
            owner = Owner.NB,
            address = address,
            contactPerson = contactPerson,
            note = note,
            callbackUrl = callbackUrl
        )
}
