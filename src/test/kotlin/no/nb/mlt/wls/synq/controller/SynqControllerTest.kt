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
import no.nb.mlt.wls.application.synqapi.synq.AttributeValue
import no.nb.mlt.wls.application.synqapi.synq.Position
import no.nb.mlt.wls.application.synqapi.synq.Product
import no.nb.mlt.wls.application.synqapi.synq.SynqBatchMoveItemPayload
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatus
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatusUpdatePayload
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.infrastructure.callbacks.InventoryNotifierAdapter
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.toItem
import no.nb.mlt.wls.infrastructure.repositories.item.toMongoItem
import no.nb.mlt.wls.infrastructure.repositories.order.OrderMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
import no.nb.mlt.wls.infrastructure.synq.toSynqOwner
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
                inventoryNotifierAdapterMock.orderChanged(any())
            }.answers { }

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", toSynqOwner(order.hostName), order.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus().isOk

            val res = orderRepository.findByHostNameAndHostOrderId(order.hostName, order.hostOrderId).awaitSingle()

            assertThat(res).isNotNull
            assertThat(res.status).isEqualTo(Order.Status.IN_PROGRESS)

            verify { inventoryNotifierAdapterMock.orderChanged(order.copy(status = Order.Status.IN_PROGRESS)) }
        }

    @Test
    fun `updateOrder without user returns 401`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", toSynqOwner(order.hostName), order.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus().isUnauthorized
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
                .uri("/order-update/{owner}/{hostOrderId}", toSynqOwner(order.hostName), order.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus().isForbidden
        }

    @Test
    fun `updateOrder with empty hostOrderId returns 400`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", toSynqOwner(order.hostName), " ")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus().isBadRequest
        }

    @Test
    fun `updateOrder with invalid payload returns 400`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", toSynqOwner(order.hostName), order.hostOrderId)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload.copy(warehouse = ""))
                .exchange()
                .expectStatus().isBadRequest
        }

    @Test
    fun `updateOrder with unknown order returns 404`() =
        runTest {
            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/order-update/{owner}/{hostOrderId}", toSynqOwner(order.hostName), 404)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(orderStatusUpdatePayload)
                .exchange()
                .expectStatus().isNotFound
        }

    @Test
    fun `updateItem correct payload updates item and sends callback`() =
        runTest {
            every {
                inventoryNotifierAdapterMock.itemChanged(any())
            }.answers { }

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload)
                .exchange()
                .expectStatus().isOk

            val item1 = itemRepository.findByHostNameAndHostId(item1.hostName, item1.hostId).awaitSingle()

            assertThat(item1).isNotNull
            assertThat(item1.location).isEqualTo(batchMoveItemPayload.location)
            assertThat(item1.quantity).isEqualTo(batchMoveItemPayload.loadUnit[0].quantityOnHand)

            val item2 = itemRepository.findByHostNameAndHostId(item2.hostName, item2.hostId).awaitSingle()

            assertThat(item2.location).isEqualTo(batchMoveItemPayload.location)
            assertThat(item2.quantity).isEqualTo(batchMoveItemPayload.loadUnit[1].quantityOnHand)

            verify { inventoryNotifierAdapterMock.itemChanged(item1.toItem()) }
            verify { inventoryNotifierAdapterMock.itemChanged(item2.toItem()) }
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
                .expectStatus().isUnauthorized
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
                .expectStatus().isForbidden
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
                .bodyValue(batchMoveItemPayload.copy(loadUnit = listOf(product1.copy(productId = "unknown"))))
                .exchange()
                .expectStatus().isNotFound
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
                .expectStatus().isBadRequest

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload.copy(loadUnit = listOf(product1.copy(quantityOnHand = -1))))
                .exchange()
                .expectStatus().isBadRequest
        }

    @Test
    fun `updateItem with no callbackUrl still updates the item`() =
        runTest {
            every {
                inventoryNotifierAdapterMock.itemChanged(any())
            }.answers { }

            itemRepository.save(item1.copy(callbackUrl = null, hostId = "test-item").toMongoItem()).awaitSingle()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_synq")))
                .put()
                .uri("/move-item")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(batchMoveItemPayload.copy(loadUnit = listOf(product1.copy(productId = "test-item"))))
                .exchange()
                .expectStatus().isOk

            val item = itemRepository.findByHostNameAndHostId(item1.hostName, "test-item").awaitSingle()

            assertThat(item).isNotNull
            assertThat(item.location).isEqualTo(batchMoveItemPayload.location)
            assertThat(item.quantity).isEqualTo(batchMoveItemPayload.loadUnit[0].quantityOnHand)
        }

// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// Test Help //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    private val product1 =
        Product(
            confidentialProduct = false,
            hostName = "AXIELL",
            productId = "mlt-12345",
            productOwner = "NB",
            productVersionId = "Default",
            quantityOnHand = 1,
            suspect = false,
            attributeValue =
                listOf(
                    AttributeValue(
                        name = "materialStatus",
                        value = "Available"
                    )
                ),
            position =
                Position(
                    xPosition = 1,
                    yPosition = 1,
                    zPosition = 1
                )
        )

    private val product2 =
        Product(
            confidentialProduct = false,
            hostName = "AXIELL",
            productId = "mlt-54321",
            productOwner = "AV",
            productVersionId = "Default",
            quantityOnHand = 69,
            suspect = false,
            attributeValue =
                listOf(
                    AttributeValue(
                        name = "materialStatus",
                        value = "Available"
                    )
                ),
            position =
                Position(
                    xPosition = 1,
                    yPosition = 1,
                    zPosition = 1
                )
        )

    private val item1 =
        Item(
            hostId = "mlt-12345",
            hostName = HostName.AXIELL,
            description = "Test item",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.FRYS,
            packaging = Packaging.BOX,
            callbackUrl = "https://callback-wls.no/item",
            location = "UNKNOWN",
            quantity = 0
        )

    private val item2 =
        Item(
            hostId = "mlt-54321",
            hostName = HostName.AXIELL,
            description = "Item test",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.FRYS,
            packaging = Packaging.BOX,
            callbackUrl = "https://callback-wls.no/item",
            location = "UNKNOWN",
            quantity = 0
        )

    private val order =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "order-12345",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(Order.OrderItem(item1.hostId, Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            contactPerson = "contactPerson",
            address =
                Order.Address(
                    recipient = "recipient",
                    addressLine1 = "addressLine1",
                    null,
                    null,
                    null,
                    null,
                    null
                ),
            callbackUrl = "https://callback-wls.no/order",
            note = "note"
        )

    private val orderStatusUpdatePayload =
        SynqOrderStatusUpdatePayload(
            prevStatus = SynqOrderStatus.ALLOCATED,
            status = SynqOrderStatus.RELEASED,
            hostName = HostName.AXIELL,
            warehouse = "Sikringmagasin_2"
        )

    private val batchMoveItemPayload =
        SynqBatchMoveItemPayload(
            tuId = "6942066642",
            location = "SYNQ_WAREHOUSE",
            prevLocation = "WS_PLUKKSENTER_1",
            loadUnit = listOf(product1, product2),
            user = "per.person@nb.no",
            warehouse = "Sikringsmagasin_2"
        )

    fun populateDb() {
        // Make sure we start with clean DB instance for each test
        runBlocking {
            itemRepository.deleteAll().thenMany(itemRepository.saveAll(listOf(item1.toMongoItem(), item2.toMongoItem()))).awaitLast()

            orderRepository.deleteAll().then(orderRepository.save(order.toMongoOrder())).awaitSingle()
        }
    }
}
