package no.nb.mlt.wls.logistics

import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.application.logisticsapi.ApiDetailedOrder
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.DELIMITER
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.toMongoItem
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.OrderMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBodyList

@EnableTestcontainers
@TestInstance(PER_CLASS)
@AutoConfigureWebTestClient
@ExtendWith(MockKExtension::class)
@EnableMongoRepositories("no.nb.mlt.wls")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class LogisticsControllerTest(
    @Autowired val applicationContext: ApplicationContext,
    @Autowired val orderRepositoryAdapter: MongoOrderRepositoryAdapter,
    @Autowired val mongoItemRepository: ItemMongoRepository,
    @Autowired val mongoOrderRepository: OrderMongoRepository
) {
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        webTestClient =
            WebTestClient
                .bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .configureClient()
                .baseUrl("/hermes/logistics/v1")
                .build()

        populateDb()
    }

    @Test
    fun `getDetailedOrders returns single order with status 200 OK`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .get()
            .uri { builder ->
                builder
                    .path("/order")
                    .queryParam("hostNames", arrayOf(HostName.AXIELL))
                    .queryParam("hostId", testOrder.hostOrderId)
                    .build()
            }.accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList<ApiDetailedOrder>()
            .hasSize(1)
    }

    @Test
    fun `getDetailedOrders returns single order with storage-specific order id`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .get()
            .uri { builder ->
                builder
                    .path("/order")
                    .queryParam("hostId", testOrder.hostName.toString() + "-ABC" + DELIMITER + testOrder.hostOrderId)
                    .build()
            }.accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList<ApiDetailedOrder>()
            .hasSize(1)
    }

    @Test
    fun `getDetailedOrders returns single order with storage-specific order id despite list of host names`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .get()
            .uri { builder ->
                builder
                    .path("/order")
                    .queryParam("hostNames", HostName.getAll())
                    .queryParam("hostId", testOrder.hostName.toString() + "-ABC" + DELIMITER + testOrder.hostOrderId)
                    .build()
            }.accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList<ApiDetailedOrder>()
            .hasSize(1)
    }

    @Test
    fun `getDetailedOrders can return orders with similar hostIds`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .get()
            .uri { builder ->
                builder
                    .path("/order")
                    .queryParam("hostNames", arrayOf(HostName.AXIELL, HostName.ASTA))
                    .queryParam("hostId", testOrder.hostOrderId)
                    .build()
            }.accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList<ApiDetailedOrder>()
            .hasSize(2)

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .get()
            .uri { builder ->
                builder
                    .path("/order")
                    .queryParam("hostId", testOrder.hostOrderId)
                    .build()
            }.accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList<ApiDetailedOrder>()
            .hasSize(2)
    }

    @Test
    fun `getDetailedOrders returns status 404 when item can not be found`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .get()
            .uri { builder ->
                builder
                    .path("/order")
                    .queryParam("hostNames", arrayOf(HostName.TEMP_STORAGE))
                    .queryParam("hostId", "AAAAA")
                    .build()
            }.accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    @EnabledIfSystemProperty(
        named = "spring.profiles.active",
        matches = "local-dev",
        disabledReason = "Only local-dev has properly configured keycloak & JWT"
    )
    fun `getDetailedOrders returns status 401 when called without authentication`() {
        webTestClient
            .mutateWith(csrf())
            .get()
            .uri { builder ->
                builder
                    .path("/order")
                    .queryParam("hostNames", arrayOf(HostName.AXIELL))
                    .queryParam("hostId", testOrder.hostOrderId)
                    .build()
            }.accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    @EnabledIfSystemProperty(
        named = "spring.profiles.active",
        matches = "local-dev",
        disabledReason = "Only local-dev has properly configured keycloak & JWT"
    )
    fun `getDetailedOrders returns status 403 when called without authentication`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order")))
            .get()
            .uri { builder ->
                builder
                    .path("/order")
                    .queryParam("hostNames", arrayOf(HostName.AXIELL))
                    .queryParam("hostId", testOrder.hostOrderId)
                    .build()
            }.accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `reportItemMissing completes with status 200 OK and updates order`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .put()
            .uri("/item/{hostName}/{hostId}/report-missing", testOrder.hostName, testOrder.orderLine.first().hostId)
            .exchange()
            .expectStatus()
            .isOk

        runTest {
            val order = orderRepositoryAdapter.getOrder(testOrder.hostName, testOrder.hostOrderId)
            assertNotNull(order)
            assert(order.orderLine.first().status == Order.OrderItem.Status.MISSING)
        }
    }

    @Test
    @EnabledIfSystemProperty(
        named = "spring.profiles.active",
        matches = "local-dev",
        disabledReason = "Only local-dev has properly configured keycloak & JWT"
    )
    fun `reportItemMissing returns status 401 when called without authentication`() {
        webTestClient
            .mutateWith(csrf())
            .put()
            .uri(
                "/item/{hostName}/{hostId}/report-missing",
                testOrder.hostName,
                testOrder.orderLine.first().hostId
            ).accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    @EnabledIfSystemProperty(
        named = "spring.profiles.active",
        matches = "local-dev",
        disabledReason = "Only local-dev has properly configured keycloak & JWT"
    )
    fun `reportItemMissing returns status 403 when called without authentication`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item")))
            .put()
            .uri(
                "/item/{hostName}/{hostId}/report-missing",
                testOrder.hostName,
                testOrder.orderLine.first().hostId
            ).accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `reportItemMissing does not update completed order when reporting missing`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .put()
            .uri("/item/{hostName}/{hostId}/report-missing", completeOrder.hostName, completeOrder.orderLine.first().hostId)
            .exchange()
            .expectStatus()
            .isOk

        runTest {
            val order = orderRepositoryAdapter.getOrder(completeOrder.hostName, completeOrder.hostOrderId)
            assertNotNull(order)
            order.orderLine.forEach { line ->
                assert(line.status != Order.OrderItem.Status.MISSING)
            }
        }
    }

    @Test
    fun `reportItemMissing completes when reporting missing on returned order`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .put()
            .uri("/item/{hostName}/{hostId}/report-missing", HostName.ASTA, "asta-01")
            .exchange()
            .expectStatus()
            .isOk

        runTest {
            val order = orderRepositoryAdapter.getOrder(HostName.ASTA, "test-01")
            assertNotNull(order)
            order.orderLine.forEach { line ->
                assert(line.status != Order.OrderItem.Status.MISSING)
            }
        }
    }

    @Test
    fun `reportItemMissing does not update partially picked orders`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .put()
            .uri("/item/{hostName}/{hostId}/report-missing", partialPickedOrder.hostName, partialPickedOrder.orderLine.first().hostId)
            .exchange()
            .expectStatus()
            .isOk

        runTest {
            val order = orderRepositoryAdapter.getOrder(partialPickedOrder.hostName, partialPickedOrder.hostOrderId)
            assertNotNull(order)
            order.orderLine.forEach { line ->
                assert(line.status != Order.OrderItem.Status.MISSING)
            }
        }
    }

    @Test
    fun `reportItemMissing completes when missing item exists in multiple orders`() {
        runTest {
            val reportTestOrder =
                createTestOrder(
                    hostName = HostName.AXIELL,
                    hostOrderId = "report-missing-test-order",
                    orderLine =
                        listOf(
                            Order.OrderItem(partialPickedOrder.orderLine.first().hostId, Order.OrderItem.Status.NOT_STARTED),
                            Order.OrderItem("ignored", Order.OrderItem.Status.NOT_STARTED),
                            Order.OrderItem("ignored2", Order.OrderItem.Status.NOT_STARTED),
                            Order.OrderItem("ignored3", Order.OrderItem.Status.NOT_STARTED)
                        ).shuffled()
                )

            mongoOrderRepository.insert(reportTestOrder.toMongoOrder()).awaitSingle()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
                .put()
                .uri("/item/{hostName}/{hostId}/report-missing", HostName.AXIELL, "axiell-02")
                .exchange()
                .expectStatus()
                .isOk

            // partial picked order would be invalid if anything has been marked missing
            val order = orderRepositoryAdapter.getOrder(partialPickedOrder.hostName, partialPickedOrder.hostOrderId)
            assertNotNull(order)
            order.orderLine.forEach { line ->
                assert(line.status != Order.OrderItem.Status.MISSING)
            }

            // the test order should only contain a single missing entry
            val testOrder = orderRepositoryAdapter.getOrder(reportTestOrder.hostName, reportTestOrder.hostOrderId)
            assertNotNull(testOrder)
            assert(testOrder.orderLine.filter { it.status == Order.OrderItem.Status.MISSING }.size == 1)
        }
    }

    // / Test Helpers
    val testOrder = createTestOrder(hostOrderId = "test-01")

    val partialPickedOrder =
        createTestOrder(
            hostOrderId = "test-02",
            status = Order.Status.IN_PROGRESS,
            orderLine =
                listOf(
                    Order.OrderItem("axiell-02", Order.OrderItem.Status.PICKED),
                    Order.OrderItem("axiell-03", Order.OrderItem.Status.NOT_STARTED)
                )
        )

    val completeOrder =
        createTestOrder(
            hostOrderId = "test-03",
            status = Order.Status.COMPLETED,
            orderLine =
                listOf(
                    Order.OrderItem("axiell-04", Order.OrderItem.Status.PICKED),
                    Order.OrderItem("axiell-05", Order.OrderItem.Status.PICKED)
                )
        )

    fun populateDb() {
        val o1 = testOrder.toMongoOrder()
        // in progress order
        val o2 = partialPickedOrder.toMongoOrder()
        // complete order
        val o3 = completeOrder.toMongoOrder()
        // partially returned order
        val o4 =
            createTestOrder(
                hostOrderId = "test-04",
                status = Order.Status.COMPLETED,
                orderLine =
                    listOf(
                        Order.OrderItem("axiell-06", Order.OrderItem.Status.PICKED),
                        Order.OrderItem("axiell-07", Order.OrderItem.Status.RETURNED)
                    )
            ).toMongoOrder()
        // fully returned order
        val o5 =
            createTestOrder(
                hostOrderId = "test-01",
                hostName = HostName.ASTA,
                status = Order.Status.RETURNED,
                orderLine =
                    listOf(
                        Order.OrderItem("asta-01", Order.OrderItem.Status.RETURNED),
                        Order.OrderItem("asta-02", Order.OrderItem.Status.RETURNED)
                    )
            ).toMongoOrder()
        val orders = listOf(o1, o2, o3, o4, o5)

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

        runBlocking {
            mongoItemRepository
                .deleteAll()
                .thenMany(mongoItemRepository.saveAll(allItems))
                .awaitLast()
            mongoOrderRepository
                .deleteAll()
                .thenMany(mongoOrderRepository.saveAll(orders))
                .awaitLast()
        }
    }
}
