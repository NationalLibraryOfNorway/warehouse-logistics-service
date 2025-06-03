package no.nb.mlt.wls.kardex.controller

import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.application.kardexapi.kardex.KardexMaterialUpdatePayload
import no.nb.mlt.wls.application.kardexapi.kardex.KardexTransactionPayload
import no.nb.mlt.wls.application.kardexapi.kardex.MotiveType
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.toMongoItem
import no.nb.mlt.wls.infrastructure.repositories.order.OrderMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
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
    @Autowired val itemRepository: ItemMongoRepository,
    @Autowired val orderRepository: OrderMongoRepository,
    @Autowired val applicationContext: ApplicationContext
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
    }

    @Test
    fun `material update with correct payload updates item`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt())
                .post()
                .uri("/material-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(materialUpdatePayload)
                .exchange()
                .expectStatus()
                .isOk()
        }
    }

    @Test
    fun `order update with correct payload updates order`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt())
                .post()
                .uri("/order-update")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue()
                .exchange()
                .expectStatus()
                .isOk()
        }
    }

    @Test
    fun `stock sync updates item`() {
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt())
                .post()
                .uri("/stock-sync")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue()
                .exchange()
                .expectStatus()
                .isOk()
        }
    }

    private val testItem1 = createTestItem(location = "")

    private val testItem2 = createTestItem(hostId = "testItem2", location = UNKNOWN_LOCATION, quantity = 0)

    private val order = createTestOrder()

    private val materialUpdatePayload =
        KardexMaterialUpdatePayload(
            hostId = testItem1.hostId,
            hostName = testItem1.hostName,
            quantity = testItem1.quantity.toDouble(),
            location = testItem1.location,
            operator = "System",
            motiveType = MotiveType.NotSet
        )

    private val orderUpdatePayload =
        KardexTransactionPayload(
            hostOrderId = TODO(),
            hostName = TODO(),
            hostId = TODO(),
            quantity = TODO(),
            motiveType = TODO(),
            location = TODO(),
            operator = TODO()
        )

    fun populateDb() {
        runBlocking {
            itemRepository.deleteAll().thenMany(
                itemRepository.saveAll(listOf(testItem1.toMongoItem(), testItem2.toMongoItem()))
            ).awaitLast()

            orderRepository.deleteAll().then(orderRepository.save(order.toMongoOrder())).awaitSingle()
        }
    }
}
