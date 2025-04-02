package no.nb.mlt.wls.synq.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.application.synqapi.synq.LoadUnit
import no.nb.mlt.wls.application.synqapi.synq.SynqBatchMoveItemPayload
import no.nb.mlt.wls.application.synqapi.synq.SynqInventoryReconciliationPayload
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatus
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatusUpdatePayload
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION
import no.nb.mlt.wls.infrastructure.callbacks.InventoryNotifierAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.toItem
import no.nb.mlt.wls.infrastructure.repositories.item.toMongoItem
import no.nb.mlt.wls.infrastructure.repositories.order.OrderMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
import no.nb.mlt.wls.infrastructure.synq.toSynqHostname
import no.nb.mlt.wls.infrastructure.synq.toSynqOwner
import no.nb.mlt.wls.toProduct
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
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.web.reactive.server.WebTestClient

@EnableTestcontainers
@TestInstance(PER_CLASS)
@AutoConfigureWebTestClient
@ExtendWith(MockKExtension::class)
@EnableMongoRepositories("no.nb.mlt.wls")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class SynqControllerTest(
    @Autowired val itemRepository: ItemMongoRepository,
    @Autowired val orderRepository: OrderMongoRepository,
    @Autowired val applicationContext: ApplicationContext
) {
    @MockkBean
    private lateinit var inventoryNotifierAdapterMock: InventoryNotifierAdapter

    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        webTestClient =
            WebTestClient
                .bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .configureClient()
                .baseUrl("/synq/v1")
                .build()

        populateDb()
    }

    @Test
    fun `updateOrder correct payload updates order and sends callback`() =
        runTest {
            every {
                inventoryNotifierAdapterMock.orderChanged(any(), any())
            }.answers { }

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", synqOwner, orderIdInSynq)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus()
                .isOk

            val res = orderRepository.findByHostNameAndHostOrderId(order.hostName, order.hostOrderId).awaitSingle()

            assertThat(res).isNotNull
            assertThat(res.status).isEqualTo(Order.Status.IN_PROGRESS)

            verify { inventoryNotifierAdapterMock.orderChanged(order.copy(status = Order.Status.IN_PROGRESS), any()) }
        }

    @Test
    fun `updateOrder short order ID updates order and sends callback`() =
        runTest {
            every {
                inventoryNotifierAdapterMock.orderChanged(any(), any())
            }.answers { }

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", synqOwner, order.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus()
                .isOk

            val res = orderRepository.findByHostNameAndHostOrderId(order.hostName, order.hostOrderId).awaitSingle()

            assertThat(res).isNotNull
            assertThat(res.status).isEqualTo(Order.Status.IN_PROGRESS)

            verify { inventoryNotifierAdapterMock.orderChanged(order.copy(status = Order.Status.IN_PROGRESS), any()) }
        }

    @Test
    fun `updateOrder without user returns 401`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", synqOwner, orderIdInSynq)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
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
    fun `updateOrder with unauthorized user returns 403`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", synqOwner, orderIdInSynq)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus()
                .isForbidden
        }

    @Test
    fun `updateOrder with empty hostOrderId returns 400`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", synqOwner, " ")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus()
                .isBadRequest
        }

    @Test
    fun `updateOrder with invalid payload returns 400`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", synqOwner, orderIdInSynq)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload.copy(warehouse = ""))
                .exchange()
                .expectStatus()
                .isBadRequest
        }

    @Test
    fun `updateOrder with unknown order returns 404`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", synqOwner, 404)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus()
                .isNotFound
        }

    @Test
    fun `updateItem correct payload updates item and sends callback`() =
        runTest {
            every {
                inventoryNotifierAdapterMock.itemChanged(any(), any())
            }.answers { }

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload)
                .exchange()
                .expectStatus()
                .isOk

            val item1 = itemRepository.findByHostNameAndHostId(testItem1.hostName, testItem1.hostId).awaitSingle()

            assertThat(item1).isNotNull
            assertThat(item1.location).isEqualTo(batchMoveItemPayload.location)
            assertThat(item1.quantity).isEqualTo(batchMoveItemPayload.loadUnit[0].quantityOnHand)

            val item2 = itemRepository.findByHostNameAndHostId(testItem2.hostName, testItem2.hostId).awaitSingle()

            assertThat(item2).isNotNull
            assertThat(item2.location).isEqualTo(batchMoveItemPayload.location)
            assertThat(item2.quantity).isEqualTo(batchMoveItemPayload.loadUnit[1].quantityOnHand)

            verify { inventoryNotifierAdapterMock.itemChanged(item1.toItem(), any()) }
            verify { inventoryNotifierAdapterMock.itemChanged(item2.toItem(), any()) }
        }

    @Test
    fun `updateItem without user returns 401`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload)
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
    fun `updateItem with unauthorized user returns 403`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item")))
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload)
                .exchange()
                .expectStatus()
                .isForbidden
        }

    @Test
    fun `updateItem with unknown item returns 404`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload.copy(loadUnit = listOf(testProduct1.copy(productId = "unknown"))))
                .exchange()
                .expectStatus()
                .isNotFound
        }

    @Test
    fun `updateItem with invalid payload returns 400`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload.copy(location = ""))
                .exchange()
                .expectStatus()
                .isBadRequest

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload.copy(loadUnit = listOf(testProduct1.copy(quantityOnHand = -1))))
                .exchange()
                .expectStatus()
                .isBadRequest
        }

    @Test
    fun `updateItem with no callbackUrl still updates the item`() =
        runTest {
            every {
                inventoryNotifierAdapterMock.itemChanged(any(), any())
            }.answers { }

            val testItem = createTestItem(callbackUrl = null, hostId = "test-item")
            itemRepository.save(testItem.toMongoItem()).awaitSingle()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload.copy(loadUnit = listOf(testProduct1.copy(productId = "test-item"))))
                .exchange()
                .expectStatus()
                .isOk

            val item = itemRepository.findByHostNameAndHostId(testItem.hostName, testItem.hostId).awaitSingle()

            assertThat(item).isNotNull
            assertThat(item.location).isEqualTo(batchMoveItemPayload.location)
            assertThat(item.quantity).isEqualTo(batchMoveItemPayload.loadUnit[0].quantityOnHand)

            verify { inventoryNotifierAdapterMock.itemChanged(item.toItem(), any()) }
        }

    @Test
    fun `Reconciliation does respond with 200 ok`() {
        val testLoadUnit =
            LoadUnit(
                productId = testProduct1.productId,
                productOwner = testProduct1.productOwner,
                quantityOnHand = 1.0,
                hostName = testProduct1.hostName,
                location = "SYNQ_WAREHOUSE",
                description = "description",
                productCategory = "PAPER",
                uom = "ESK"
            )

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
            .put()
            .uri("/inventory-reconciliation")
            .bodyValue(
                SynqInventoryReconciliationPayload(
                    loadUnit =
                        listOf(
                            testLoadUnit,
                            testLoadUnit.copy(productId = testProduct1.productId, hostName = null),
                            testLoadUnit.copy(productId = testProduct2.productId, hostName = "mellomlager")
                        )
                )
            ).exchange()
            .expectStatus()
            .isOk
    }

    private val testItem1 = createTestItem()

    private val testItem2 = createTestItem(hostId = "testItem2", location = UNKNOWN_LOCATION, quantity = 0)

    private val testProduct1 = testItem1.toProduct()

    private val testProduct2 = testItem2.toProduct()

    private val order = createTestOrder()

    private val synqOwner = toSynqOwner(order.hostName)

    private val orderIdInSynq = "${order.hostName.toString().uppercase()}-SD---${order.hostOrderId}"

    private val orderStatusUpdatePayload =
        SynqOrderStatusUpdatePayload(
            prevStatus = SynqOrderStatus.ALLOCATED,
            status = SynqOrderStatus.RELEASED,
            hostName = toSynqHostname(HostName.AXIELL),
            warehouse = "Sikringmagasin_2"
        )

    private val batchMoveItemPayload =
        SynqBatchMoveItemPayload(
            tuId = "6942066642",
            location = "WS_PLUKKSENTER_1",
            prevLocation = "SYNQ_WAREHOUSE",
            loadUnit = listOf(testProduct1, testProduct2),
            user = "per.person@nb.no",
            warehouse = "Sikringsmagasin_2"
        )

    fun populateDb() {
        runBlocking {
            itemRepository.deleteAll().thenMany(itemRepository.saveAll(listOf(testItem1.toMongoItem(), testItem2.toMongoItem()))).awaitLast()

            orderRepository.deleteAll().then(orderRepository.save(order.toMongoOrder())).awaitSingle()
        }
    }
}
