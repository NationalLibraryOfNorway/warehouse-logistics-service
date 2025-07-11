package no.nb.mlt.wls.order.controller

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.OrderLine
import no.nb.mlt.wls.application.hostapi.order.toApiPayload
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.infrastructure.repositories.event.MongoStorageEventRepository
import no.nb.mlt.wls.infrastructure.repositories.event.MongoStorageEventRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.toMongoItem
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.OrderMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
import no.nb.mlt.wls.infrastructure.synq.SynqStandardAdapter
import no.nb.mlt.wls.toOrder
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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@EnableTestcontainers
@TestInstance(PER_CLASS)
@AutoConfigureWebTestClient
@ExtendWith(MockKExtension::class)
@EnableMongoRepositories("no.nb.mlt.wls")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class OrderControllerTest(
    @Autowired val itemMongoRepository: ItemMongoRepository,
    @Autowired val applicationContext: ApplicationContext,
    @Autowired val repository: OrderMongoRepository,
    @Autowired val mongoRepository: MongoOrderRepositoryAdapter,
    @Autowired val mongoStorageEventRepository: MongoStorageEventRepository,
    @Autowired val storageEventRepositoryAdapter: MongoStorageEventRepositoryAdapter
) {
    @MockkBean
    private lateinit var synqStandardAdapterMock: SynqStandardAdapter

    @SpykBean
    private lateinit var storageEventRepository: EventRepository<StorageEvent>

    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        webTestClient =
            WebTestClient
                .bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .configureClient()
                .baseUrl("/hermes/v1/order")
                .build()

        populateDb()
    }

    @Test
    fun `createOrder with valid payload creates order and outbox message`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(testOrderPayload)
                .exchange()
                .expectStatus()
                .isCreated

            val order = mongoRepository.getOrder(testOrderPayload.hostName, testOrderPayload.hostOrderId)
            val outbox = storageEventRepositoryAdapter.getAll()

            assertThat(outbox)
                .hasSize(1)
                .filteredOn { it is OrderCreated }
                .first()
                .matches {
                    val message = it as OrderCreated
                    message.createdOrder == testOrderPayload.toOrder()
                }

            assertThat(order)
                .isNotNull
                .extracting("callbackUrl", "status")
                .containsExactly(testOrderPayload.callbackUrl, Order.Status.NOT_STARTED)
        }

    @Test
    fun `createOrder with duplicate payload returns OK but does not create outbox message`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(duplicateOrderPayload)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<ApiOrderPayload>()
                .consumeWith { response ->
                    assertThat(response.responseBody?.hostOrderId).isEqualTo(duplicateOrderPayload.hostOrderId)
                    assertThat(response.responseBody?.hostName).isEqualTo(duplicateOrderPayload.hostName)
                    assertThat(response.responseBody?.orderLine).isEqualTo(duplicateOrderPayload.orderLine)
                }

            val outbox = storageEventRepositoryAdapter.getAll()
            assertThat(outbox).isEmpty()
        }
    }

    @Test
    fun `createOrder payload with different data but same ID returns DB entry and does not create outbox message`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(
                    duplicateOrderPayload.copy(
                        orderLine = listOf(OrderLine("AAAAAAAAA", Order.OrderItem.Status.PICKED))
                    )
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<ApiOrderPayload>()
                .consumeWith { response ->
                    assertThat(response.responseBody?.orderLine).isEqualTo(duplicateOrderPayload.orderLine)
                }

            val outbox = storageEventRepositoryAdapter.getAll()
            assertThat(outbox).isEmpty()
        }
    }

    @Test
    fun `createOrder with invalid fields returns 400`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload.copy(orderLine = emptyList()))
            .exchange()
            .expectStatus()
            .isBadRequest

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload.copy(hostOrderId = ""))
            .exchange()
            .expectStatus()
            .isBadRequest

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload.copy(callbackUrl = "testing.no"))
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `getOrder returns the order`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
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
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, "not-an-id")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `getOrders returns list of orders`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
            .get()
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(ApiOrderPayload::class.java)
            .hasSize(3)
            .contains(duplicateOrderPayload)

        webTestClient
            .mutateWith(csrf())
            .mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole), SimpleGrantedAuthority("ROLE_asta"))
            ).get()
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(ApiOrderPayload::class.java)
            .hasSize(5)
            .contains(duplicateOrderPayload)

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order")))
            .get()
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(ApiOrderPayload::class.java)
            .hasSize(0)
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
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority("ROLE_asta")))
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isForbidden

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority(clientRole)))
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `Should not save order if outbox message fails to persist`() {
        runTest {
            coEvery { storageEventRepository.save(any()) } throws RuntimeException("Testing: Failed to save outbox message")
            coEvery { synqStandardAdapterMock.canHandleLocation(any()) } returns true

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(testOrderPayload)
                .exchange()
                .expectStatus()
                .is5xxServerError

            val order = mongoRepository.getOrder(testOrderPayload.hostName, testOrderPayload.hostOrderId)
            assertThat(order).isNull()

            assertThat(storageEventRepository.getAll()).isEmpty()
        }
    }

    @Test
    fun `deleteOrder with valid data deletes order`() =
        runTest {
            coEvery { synqStandardAdapterMock.canHandleLocation(any()) } returns true
            coEvery { synqStandardAdapterMock.canHandleItem(any()) } returns true
            coJustRun { synqStandardAdapterMock.deleteOrder(any(), any()) }

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .delete()
                .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isNoContent

            val order =
                repository
                    .findByHostNameAndHostOrderId(
                        duplicateOrderPayload.hostName,
                        duplicateOrderPayload.hostOrderId
                    ).awaitSingleOrNull()

            assertThat(order?.status).isEqualTo(Order.Status.DELETED)

            val outBoxMessages = storageEventRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(1)
                .first()
                .matches {
                    it is OrderDeleted && it.hostOrderId == duplicateOrderPayload.hostOrderId
                }
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
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .delete()
                .uri("/{hostName}/{hostOrderId}", orderInProgress.hostName, orderInProgress.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)

            val outBoxMessages = storageEventRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
        }
    }

    @Test
    fun `deleteOrder with blank hostOrderId returns 400`() =
        runTest {
            coEvery { synqStandardAdapterMock.canHandleLocation(any()) } returns true
            coEvery { synqStandardAdapterMock.canHandleItem(any()) } returns true
            coJustRun { synqStandardAdapterMock.deleteOrder(any(), any()) }

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .delete()
                .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, " ")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest

            val outBoxMessages = storageEventRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
        }

    @Test
    fun `deleteOrder with order that does not exist returns 404`() =
        runTest {
            coEvery {
                synqStandardAdapterMock.deleteOrder(any(), any())
            } answers {}

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .delete()
                .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, "does-not-exist")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isNotFound

            val outBoxMessages = storageEventRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
        }

    private val testOrder = createTestOrder()

    private val testOrderPayload = testOrder.toApiPayload()

    private val duplicateOrderPayload = testOrderPayload.copy(hostOrderId = "duplicate-order-id")

    private val orderInProgress = testOrder.copy(hostOrderId = "order-in-progress", status = Order.Status.IN_PROGRESS)

    private val clientRole: String = "ROLE_${HostName.AXIELL.name.lowercase()}"

    fun populateDb() {
        runBlocking {
            mongoStorageEventRepository.deleteAll().awaitSingleOrNull()

            val o1 = createTestOrder(hostOrderId = "axiell-01", hostName = HostName.AXIELL).toMongoOrder()
            val o2 = createTestOrder(hostOrderId = "axiell-02", hostName = HostName.AXIELL).toMongoOrder()
            val o3 = createTestOrder(hostOrderId = "asta-01", hostName = HostName.ASTA).toMongoOrder()
            val o4 = createTestOrder(hostOrderId = "asta-02", hostName = HostName.ASTA).toMongoOrder()
            val o5 = createTestOrder(hostOrderId = "alma-01", hostName = HostName.ALMA).toMongoOrder()
            val o6 = createTestOrder(hostOrderId = "alma-02", hostName = HostName.ALMA).toMongoOrder()
            val orders = listOf(o1, o2, o3, o4, o5, o6, duplicateOrderPayload.toOrder().toMongoOrder())

            repository.deleteAll().thenMany(repository.saveAll(orders)).awaitLast()

            // Create all items in orders in the database
            val allItems =
                orders.flatMap { order ->
                    order.orderLine.map { item ->
                        createTestItem(
                            hostId = item.hostId,
                            hostName = order.hostName
                        ).toMongoItem()
                    }
                }

            itemMongoRepository
                .deleteAll()
                .thenMany(itemMongoRepository.saveAll(allItems))
                .awaitLast()
        }
    }
}
