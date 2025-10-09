package no.nb.mlt.wls.kardex.controller

import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.application.kardexapi.kardex.KardexMaterialPayload
import no.nb.mlt.wls.application.kardexapi.kardex.KardexSyncMaterialPayload
import no.nb.mlt.wls.application.kardexapi.kardex.KardexTransactionPayload
import no.nb.mlt.wls.application.kardexapi.kardex.MotiveType
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.toMongoItem
import no.nb.mlt.wls.infrastructure.repositories.order.OrderMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
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

@EnableTestcontainers
@TestInstance(PER_CLASS)
@ExtendWith(MockKExtension::class)
@EnableMongoRepositories("no.nb.mlt.wls")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class KardexControllerTest(
    @param:Autowired val itemRepository: ItemMongoRepository,
    @param:Autowired val orderRepository: OrderMongoRepository,
    @param:Autowired val applicationContext: ApplicationContext
) {
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        webTestClient =
            WebTestClient
                .bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .configureClient()
                .baseUrl("/hermes/kardex/v1")
                .build()

        populateDb()
    }

    @Test
    fun `material update with correct payload updates item`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/material-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(materialUpdatePayload))
                .exchange()
                .expectStatus()
                .isOk()

            val item = itemRepository.findByHostNameAndHostId(testItem1.hostName, testItem1.hostId).awaitSingle()
            assert(item.location == materialUpdatePayload.location)
        }
    }

    @Test
    fun `material update with payload with UNKNOWN HostName updates item`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/material-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(materialUpdatePayload.copy(hostName = HostName.UNKNOWN.toString())))
                .exchange()
                .expectStatus()
                .isOk()

            val item = itemRepository.findByHostNameAndHostId(testItem1.hostName, testItem1.hostId).awaitSingle()
            assert(item.location == materialUpdatePayload.location)
        }
    }

    @Test
    fun `material update with payload without HostName updates item`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/material-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(materialUpdatePayload.copy(hostName = "")))
                .exchange()
                .expectStatus()
                .isOk()

            val item = itemRepository.findByHostNameAndHostId(testItem1.hostName, testItem1.hostId).awaitSingle()
            assert(item.location == materialUpdatePayload.location)
        }
    }

    @Test
    fun `material update with invalid payload fails`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/material-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf("gibberish"))
                .exchange()
                .expectStatus()
                .isBadRequest()
        }
    }

    @Test
    fun `material update for missing item fails`() {
        runTest {
            val item = itemRepository.findByHostNameAndHostId(testItem1.hostName, "non-existing").awaitSingleOrNull()

            assert(item == null)
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/material-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(materialUpdatePayload.copy(hostId = "non-existing")))
                .exchange()
                .expectStatus()
                .isNotFound
        }
    }

    @Test
    fun `order update with correct payload updates order`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/order-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(orderUpdatePayload))
                .exchange()
                .expectStatus()
                .isOk()

            val order = orderRepository.findByHostNameAndHostOrderId(testOrder.hostName, testOrder.hostOrderId).awaitSingle()
            assert(order.status != testOrder.status)
            assert(order.status == Order.Status.IN_PROGRESS)

            val item = itemRepository.findByHostNameAndHostId(testItem1.hostName, testItem1.hostId).awaitSingle()
            assert(item.quantity == orderUpdatePayload.quantity.toInt())
            assert(item.quantity != testItem1.quantity)
        }
    }

    @Test
    fun `order update with payload with UNKNOWN HostName updates order`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/order-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(orderUpdatePayload.copy(hostName = HostName.UNKNOWN.toString())))
                .exchange()
                .expectStatus()
                .isOk()

            val order = orderRepository.findByHostNameAndHostOrderId(testOrder.hostName, testOrder.hostOrderId).awaitSingle()
            assert(order.status != testOrder.status)
            assert(order.status == Order.Status.IN_PROGRESS)

            val item = itemRepository.findByHostNameAndHostId(testItem1.hostName, testItem1.hostId).awaitSingle()
            assert(item.quantity == orderUpdatePayload.quantity.toInt())
            assert(item.quantity != testItem1.quantity)
        }
    }

    @Test
    fun `order update with payload without HostName updates order`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/order-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(orderUpdatePayload.copy(hostName = "")))
                .exchange()
                .expectStatus()
                .isOk()

            val order = orderRepository.findByHostNameAndHostOrderId(testOrder.hostName, testOrder.hostOrderId).awaitSingle()
            assert(order.status != testOrder.status)
            assert(order.status == Order.Status.IN_PROGRESS)

            val item = itemRepository.findByHostNameAndHostId(testItem1.hostName, testItem1.hostId).awaitSingle()
            assert(item.quantity == orderUpdatePayload.quantity.toInt())
            assert(item.quantity != testItem1.quantity)
        }
    }

    @Test
    fun `order update with invalid payload fails`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/order-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(orderUpdatePayload.copy(hostOrderId = "")))
                .exchange()
                .expectStatus()
                .isBadRequest()
        }
    }

    @Test
    fun `order update on missing order fails`() {
        runTest {
            val order = orderRepository.findByHostNameAndHostOrderId(testOrder.hostName, "non-existing").awaitSingleOrNull()
            assert(order == null)

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/order-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(orderUpdatePayload.copy(hostOrderId = "non-existing")))
                .exchange()
                .expectStatus()
                .isNotFound
        }
    }

    @Test
    fun `stock sync updates item`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/stock-sync")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(stockSyncPayload))
                .exchange()
                .expectStatus()
                .isOk()
        }
    }

    @Test
    fun `stock sync for missing item completes without creating item`() {
        runTest {
            val nonExistingItem = "non-existing"
            assertThat(itemRepository.findByHostNameAndHostId(testItem1.hostName, nonExistingItem).awaitSingleOrNull()).isNull()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_kardex")))
                .post()
                .uri("/stock-sync")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(listOf(stockSyncPayload.copy(hostId = nonExistingItem)))
                .exchange()
                .expectStatus()
                .isOk()

            assertThat(itemRepository.findByHostNameAndHostId(testItem1.hostName, nonExistingItem).awaitSingleOrNull()).isNull()
        }
    }

    private val testItem1 = createTestItem()

    private val testItem2 = createTestItem(hostId = "testItem2", location = UNKNOWN_LOCATION, quantity = 0)

    private val testOrder = createTestOrder()

    private val materialUpdatePayload =
        KardexMaterialPayload(
            hostId = testItem1.hostId,
            hostName = testItem1.hostName.toString(),
            quantity = testItem1.quantity.toDouble(),
            location = testItem1.location,
            operator = "System",
            motiveType = MotiveType.NotSet
        )

    private val orderUpdatePayload =
        KardexTransactionPayload(
            hostOrderId = testOrder.hostOrderId,
            hostName = testOrder.hostName.toString(),
            hostId = testItem1.hostId,
            quantity = 0.0,
            motiveType = MotiveType.NotSet,
            location = testItem1.location,
            operator = "System"
        )

    private val stockSyncPayload =
        KardexSyncMaterialPayload(
            hostId = testItem1.hostId,
            hostName = testItem1.hostName.toString(),
            quantity = 1.0,
            location = testItem1.location
        )

    fun populateDb() {
        runBlocking {
            itemRepository
                .deleteAll()
                .thenMany(
                    itemRepository.saveAll(listOf(testItem1.toMongoItem(), testItem2.toMongoItem()))
                ).awaitLast()

            orderRepository.deleteAll().then(orderRepository.save(testOrder.toMongoOrder())).awaitSingle()
        }
    }
}
