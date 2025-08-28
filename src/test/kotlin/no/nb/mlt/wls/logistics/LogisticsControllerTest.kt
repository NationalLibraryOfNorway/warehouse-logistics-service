package no.nb.mlt.wls.logistics

import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.runBlocking
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.application.logisticsapi.ApiDetailedOrder
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.infrastructure.repositories.event.MongoStorageEventRepository
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.toMongoItem
import no.nb.mlt.wls.infrastructure.repositories.order.MongoOrderRepositoryAdapter
import no.nb.mlt.wls.infrastructure.repositories.order.OrderMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
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
    @Autowired val mongoOrderRepository: OrderMongoRepository,
    @Autowired val mongoStorageEventRepository: MongoStorageEventRepository
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
    fun `getDetailedOrders returns orders with same lines`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_logistics")))
            .get()
            .uri { builder ->
                builder
                    .path("/order")
                    .queryParam("hostNames", listOf(HostName.AXIELL, HostName.ASTA))
                    .queryParam("hostId", "test-01")
                    .build()
            }.accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList<ApiDetailedOrder>()
            .hasSize(1)
    }

    fun populateDb() {
        val o1 = createTestOrder(hostOrderId = "test-01").toMongoOrder()
        // in progress order
        val o2 =
            createTestOrder(
                hostOrderId = "test-02",
                status = Order.Status.IN_PROGRESS,
                orderLine =
                    listOf(
                        Order.OrderItem("axiell-01", Order.OrderItem.Status.PICKED),
                        Order.OrderItem("axiell-02", Order.OrderItem.Status.NOT_STARTED)
                    )
            ).toMongoOrder()
        // complete order
        val o3 =
            createTestOrder(
                hostOrderId = "test-03",
                status = Order.Status.COMPLETED,
                orderLine =
                    listOf(
                        Order.OrderItem("axiell-01", Order.OrderItem.Status.PICKED),
                        Order.OrderItem("axiell-02", Order.OrderItem.Status.PICKED)
                    )
            ).toMongoOrder()
        // partially returned order
        val o4 =
            createTestOrder(
                hostOrderId = "test-04",
                status = Order.Status.COMPLETED,
                orderLine =
                    listOf(
                        Order.OrderItem("axiell-01", Order.OrderItem.Status.PICKED),
                        Order.OrderItem("axiell-02", Order.OrderItem.Status.RETURNED)
                    )
            ).toMongoOrder()
        // fully returned order
        val o5 =
            createTestOrder(
                hostOrderId = "test-05",
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
