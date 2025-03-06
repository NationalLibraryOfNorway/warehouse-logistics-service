package no.nb.mlt.wls.order.controller

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.coEvery
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.TestcontainerInitializer.Companion.MAILHOG_HTTP_PORT
import no.nb.mlt.wls.TestcontainerInitializer.Companion.MailhogContainer
import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.OrderLine
import no.nb.mlt.wls.application.hostapi.order.toApiOrderPayload
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.outboxMessages.OrderCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OrderDeleted
import no.nb.mlt.wls.domain.model.outboxMessages.OrderUpdated
import no.nb.mlt.wls.domain.ports.inbound.toOrder
import no.nb.mlt.wls.domain.ports.outbound.EmailRepository
import no.nb.mlt.wls.domain.ports.outbound.OutboxRepository
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.MongoItem
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.OrderMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
import no.nb.mlt.wls.infrastructure.repositories.outbox.MongoOutboxRepository
import no.nb.mlt.wls.infrastructure.repositories.outbox.MongoOutboxRepositoryAdapter
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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
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
    @Autowired val emailRepository: EmailRepository,
    @Autowired val repository: OrderMongoRepository,
    @Autowired val mongoRepository: MongoOrderRepositoryAdapter,
    @Autowired val mongoOutboxRepository: MongoOutboxRepository,
    @Autowired val outboxRepositoryAdapter: MongoOutboxRepositoryAdapter
) {
//    @Autowired
//    private lateinit var outboxRepository: OutboxRepository

    @MockkBean
    private lateinit var synqAdapterMock: SynqAdapter

    @SpykBean
    private lateinit var outboxRepository: OutboxRepository

    private lateinit var webTestClient: WebTestClient

    val clientRole: String = "ROLE_${HostName.AXIELL.name.lowercase()}"

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
    fun `createOrder with valid payload creates order and outbox message`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(testOrderPayload)
                .exchange()
                .expectStatus().isCreated

            val order = mongoRepository.getOrder(testOrderPayload.hostName, testOrderPayload.hostOrderId)
            val outbox = outboxRepositoryAdapter.getAll()

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
    fun `createOrder with valid payload also creates email`() {
        runTest {
            coEvery {
                synqAdapterMock.createOrder(any())
            } answers {}
            coEvery { synqAdapterMock.canHandleLocation(any()) } returns true
            emailRepository.createHostEmail(testOrderPayload.hostName, "test@example.com")

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(testOrderPayload)
                .exchange()
                .expectStatus().isCreated

            // FIXME - This test finishes before emails go through
            // Letting it run for too long will also crash, since the application will try
            // To process messages in the outbox
            // Wait a few seconds for the emails to go through
            async {
                withContext(Dispatchers.Default) {
                    delay(200L)
                }
            }.await()

            val mailhogUrl = "http://" + MailhogContainer.host + ":" + MailhogContainer.getMappedPort(MAILHOG_HTTP_PORT) + "/api/v2/messages"

            // Create a temporary new client to check emails
            WebTestClient
                .bindToServer()
                .build()
                .get()
                .uri(mailhogUrl)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectBody()
                .jsonPath("$.total").value<Int> {
                    if (it == 0) throw RuntimeException("No emails found", null)
                }
        }
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
                .expectStatus().isOk
                .expectBody<ApiOrderPayload>()
                .consumeWith { response ->
                    assertThat(response.responseBody?.hostOrderId).isEqualTo(duplicateOrderPayload.hostOrderId)
                    assertThat(response.responseBody?.hostName).isEqualTo(duplicateOrderPayload.hostName)
                    assertThat(response.responseBody?.orderLine).isEqualTo(duplicateOrderPayload.orderLine)
                }

            val outbox = outboxRepositoryAdapter.getAll()
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
                )
                .exchange()
                .expectStatus().isOk
                .expectBody<ApiOrderPayload>()
                .consumeWith { response ->
                    assertThat(response.responseBody?.orderLine).isEqualTo(duplicateOrderPayload.orderLine)
                }

            val outbox = outboxRepositoryAdapter.getAll()
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
            .expectStatus().isBadRequest

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload.copy(hostOrderId = ""))
            .exchange()
            .expectStatus().isBadRequest

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testOrderPayload.copy(callbackUrl = "testing.no"))
            .exchange()
            .expectStatus().isBadRequest
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
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
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
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority("ROLE_asta")))
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isForbidden

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority(clientRole)))
            .get()
            .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, duplicateOrderPayload.hostOrderId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `Should not save order if outbox message fails to persist`() {
        // Just calling "toOrder" on a payload fails, as it does not
        // do the same mapping as the OrderDTO
        val testOrder = testOrderPayload.toCreateOrderDTO().toOrder()

        runTest {
            coEvery { outboxRepository.save(any()) } throws RuntimeException("Testing: Failed to save outbox message")
            coEvery { synqAdapterMock.canHandleLocation(any()) } returns true

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(testOrderPayload)
                .exchange()
                .expectStatus().is5xxServerError

            val order = mongoRepository.getOrder(testOrderPayload.hostName, testOrderPayload.hostOrderId)
            assertThat(order).isNull()

            assertThat(outboxRepository.getAll()).isEmpty()
        }
    }

    @Test
    fun `updateOrder with valid payload updates order and creates outbox message`() {
        val testPayload =
            duplicateOrderPayload.copy(
                contactPerson = "new person",
                callbackUrl = "https://new-callback.com/order",
                orderLine = listOf(OrderLine("item-123", null))
            )

        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
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

            val outBoxMessages = outboxRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(1)
                .first()
                .matches {
                    it is OrderUpdated && it.updatedOrder == testPayload.toOrder()
                }
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
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .put()
                .bodyValue(testUpdatePayload)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)

            val outBoxMessages = outboxRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
        }
    }

    @Test
    fun `updateOrder with invalid fields returns 400`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .put()
                .bodyValue(testOrderPayload.copy(hostOrderId = ""))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest

            val outBoxMessages = outboxRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
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
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .put()
                .bodyValue(testUpdatePayload)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)

            val outBoxMessages = outboxRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
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
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .put()
                .bodyValue(testPayload)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)

            val outBoxMessages = outboxRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
        }
    }

    @Test
    fun `updateOrder when order is complete returns 409`() {
        val testPayload = completeOrder.toApiOrderPayload().copy(orderType = Order.Type.DIGITIZATION)
        runTest {
            repository.save(completeOrder.toMongoOrder()).awaitSingle()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .put()
                .bodyValue(testPayload)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)

            val outBoxMessages = outboxRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
        }
    }

    @Test
    fun `deleteOrder with valid data deletes order`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
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

            val outBoxMessages = outboxRepositoryAdapter.getAll()
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
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)

            val outBoxMessages = outboxRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
        }
    }

    @Test
    fun `deleteOrder with blank hostOrderId returns 400`() =
        runTest {
            coEvery {
                synqAdapterMock.deleteOrder(any(), any())
            } answers {}

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .delete()
                .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, " ")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest

            val outBoxMessages = outboxRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
        }

    @Test
    fun `deleteOrder with order that does not exist returns 404`() =
        runTest {
            coEvery {
                synqAdapterMock.deleteOrder(any(), any())
            } answers {}

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
                .delete()
                .uri("/{hostName}/{hostOrderId}", duplicateOrderPayload.hostName, "does-not-exist")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound

            val outBoxMessages = outboxRepositoryAdapter.getAll()
            assertThat(outBoxMessages)
                .hasSize(0)
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
            callbackUrl = "https://callback-wls.no/order"
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
            callbackUrl = "https://callback-wls.no/order"
        )

    private val orderInProgress =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "order-in-progress",
            status = Order.Status.IN_PROGRESS,
            orderLine = listOf(Order.OrderItem("item-123", Order.OrderItem.Status.NOT_STARTED)),
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
            callbackUrl = "https://callback-wls.no/order"
        )

    private val completeOrder =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "order-completed",
            status = Order.Status.COMPLETED,
            orderLine =
                listOf(
                    Order.OrderItem("item-123", Order.OrderItem.Status.PICKED)
                ),
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
            callbackUrl = "https://callback-wls.no/order"
        )

    /**
     * Populate the database with items and orders for testing
     */

    fun populateDb() {
        runBlocking {
            mongoOutboxRepository.deleteAll().awaitSingleOrNull()
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
                                location = "location",
                                quantity = 1,
                                callbackUrl = "https://callback-wls.no/item"
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
            address = address,
            contactPerson = contactPerson,
            note = note,
            callbackUrl = callbackUrl
        )
}
