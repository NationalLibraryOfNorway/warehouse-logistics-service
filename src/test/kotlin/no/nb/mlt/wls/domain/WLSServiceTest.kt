package no.nb.mlt.wls.domain

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Mono

class WLSServiceTest {
    private val orderRepoMock = mockk<OrderRepository>()
    private val itemRepoMock = mockk<ItemRepository>()
    private val storageSystemRepoMock = mockk<StorageSystemFacade>()
    private val inventoryNotifierMock = mockk<InventoryNotifier>()

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
    }

    @Test
    @Suppress("ReactiveStreamsUnusedPublisher")
    fun `addItem should save and return new item when it does not exists`() {
        val expectedItem = testItem.copy()
        coEvery { itemRepoMock.getItem(any(), any()) } answers { null }
        coEvery { itemRepoMock.createItem(any()) } answers { Mono.just(expectedItem) }
        coJustRun { storageSystemRepoMock.createItem(any()) }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            val itemResult =
                cut.addItem(
                    ItemMetadata(
                        hostId = testItem.hostId,
                        hostName = testItem.hostName,
                        description = testItem.description,
                        itemCategory = testItem.itemCategory,
                        preferredEnvironment = testItem.preferredEnvironment,
                        packaging = testItem.packaging,
                        owner = testItem.owner,
                        callbackUrl = testItem.callbackUrl
                    )
                )

            assertThat(itemResult).isEqualTo(expectedItem)
            coVerify(exactly = 1) { itemRepoMock.createItem(any()) }
            coVerify(exactly = 1) { storageSystemRepoMock.createItem(any()) }
        }
    }

    @Test
    @Suppress("ReactiveStreamsUnusedPublisher")
    fun `addItem should not save new item but return existing item if it already exists`() {
        coEvery { itemRepoMock.getItem(testItem.hostName, testItem.hostId) } answers { testItem.copy() }
        coEvery { itemRepoMock.createItem(any()) } answers { Mono.just(testItem.copy()) }
        coJustRun { storageSystemRepoMock.createItem(any()) }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)

        runTest {
            val newItem =
                cut.addItem(
                    ItemMetadata(
                        hostId = testItem.hostId,
                        hostName = testItem.hostName,
                        description = testItem.description,
                        itemCategory = testItem.itemCategory,
                        preferredEnvironment = testItem.preferredEnvironment,
                        packaging = testItem.packaging,
                        owner = testItem.owner,
                        callbackUrl = testItem.callbackUrl
                    )
                )

            assertThat(newItem).isEqualTo(testItem)

            coVerify(exactly = 0) { itemRepoMock.createItem(any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.createItem(any()) }
        }
    }

    @Test
    fun `getItem should return requested item when it exists in DB`() {
        val expectedItem = testItem.copy()

        coEvery { itemRepoMock.getItem(HostName.AXIELL, "12345") } answers { expectedItem }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            val itemResult = cut.getItem(HostName.AXIELL, "12345")
            assertThat(itemResult).isEqualTo(expectedItem)
        }
    }

    @Test
    fun `getItem should return null if item does not exist`() {
        coEvery { itemRepoMock.getItem(HostName.AXIELL, "12345") } answers { null }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            val itemResult = cut.getItem(HostName.AXIELL, "12345")
            assertThat(itemResult).isEqualTo(null)
        }
    }

    @Test
    fun `moveItem should return when item successfully moves`() {
        val expectedItem =
            testItem.copy(
                location = "Somewhere nice",
                quantity = 1
            )
        coEvery { itemRepoMock.getItem(any(), any()) } returns testItem
        coEvery { itemRepoMock.moveItem(any(), any(), any(), any()) } returns expectedItem
        every { inventoryNotifierMock.itemChanged(any()) } answers {}

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            val movedItem = cut.moveItem(testMoveItemPayload)
            assertThat(movedItem).isEqualTo(expectedItem)

            coVerify(exactly = 1) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 1) { itemRepoMock.moveItem(any(), any(), any(), any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.createOrder(any()) }
        }
    }

    @Test
    fun `moveItem should fail when item does not exist`() {
        coEvery { itemRepoMock.moveItem(any(), any(), any(), any()) } throws ItemNotFoundException("Item not found")

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            assertThrows<RuntimeException> {
                cut.moveItem(testMoveItemPayload)
            }

            coVerify(exactly = 1) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepoMock.moveItem(any(), any(), any(), any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.createOrder(any()) }
        }
    }

    @Test
    fun `moveItem throws when count is invalid`() {
        coEvery { itemRepoMock.moveItem(any(), any(), -1, any()) } throws ValidationException("Location cannot be blank")

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            assertThrows<RuntimeException> {
                cut.moveItem(testMoveItemPayload.copy(quantity = -1))
            }

            coVerify(exactly = 0) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepoMock.moveItem(any(), any(), any(), any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.createOrder(any()) }
        }
    }

    @Test
    fun `moveItem throws when location is blank`() {
        coEvery { itemRepoMock.moveItem(any(), any(), any(), any()) } throws ValidationException("Item not found")

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            assertThrows<RuntimeException> {
                cut.moveItem(testMoveItemPayload.copy(location = "  "))
            }

            coVerify(exactly = 0) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepoMock.moveItem(any(), any(), any(), any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.createOrder(any()) }
        }
    }

    @Test
    fun `createOrder should save order in db and storage system`() {
        val expectedOrder = testOrder.copy()

        coEvery { orderRepoMock.getOrder(any(), any()) } answers { null }
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { orderRepoMock.createOrder(any()) } answers { expectedOrder }
        coJustRun { storageSystemRepoMock.createOrder(any()) }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            val order = cut.createOrder(testOrder.toCreateOrderDTO())

            assertThat(order).isEqualTo(expectedOrder)

            coVerify(exactly = 1) { orderRepoMock.createOrder(any()) }
            coVerify(exactly = 1) { storageSystemRepoMock.createOrder(any()) }
        }
    }

    @Test
    fun `createOrder should return existing order when trying to create one with same id and host`() {
        coEvery {
            orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId)
        } answers { testOrder.copy() }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            val order =
                cut.createOrder(
                    testOrder.toCreateOrderDTO().copy(callbackUrl = "https://newurl.com")
                )

            assertThat(order).isEqualTo(testOrder)
            assertThat(order.callbackUrl).isEqualTo(testOrder.callbackUrl)
            coVerify(exactly = 0) { orderRepoMock.createOrder(any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.createOrder(any()) }
        }
    }

    // Test create order when order items do not exist
    @Test
    fun `createOrder should fail if some of the items does not exist`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } answers { null }
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { false }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            assertThrows<ValidationException> {
                cut.createOrder(testOrder.toCreateOrderDTO())
            }
            coVerify(exactly = 0) { orderRepoMock.createOrder(any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.createOrder(any()) }
        }
    }

    @Test
    fun `deleteOrder should complete when order exists`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } returns testOrder
        coJustRun { orderRepoMock.deleteOrder(any(), any()) }
        coJustRun { storageSystemRepoMock.deleteOrder(any()) }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)

        runTest {
            cut.deleteOrder(HostName.AXIELL, "12345")
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 1) { orderRepoMock.deleteOrder(any(), any()) }
            coVerify(exactly = 1) { storageSystemRepoMock.deleteOrder(any()) }
        }
    }

    @Test
    fun `deleteOrder should fail when order does not exist in storage system`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } returns testOrder
        coEvery { storageSystemRepoMock.deleteOrder(any()) } throws StorageSystemException("Order not found", null)

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)

        runTest {
            assertThrows<StorageSystemException> {
                cut.deleteOrder(HostName.AXIELL, "12345")
            }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 1) { storageSystemRepoMock.deleteOrder(any()) }
            coVerify(exactly = 0) { orderRepoMock.deleteOrder(any(), any()) }
        }
    }

    @Test
    fun `deleteOrder should fail when order does not exist in WLS database`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } throws OrderNotFoundException("Order not found")
        coJustRun { storageSystemRepoMock.deleteOrder(any()) }
        coEvery { orderRepoMock.deleteOrder(any(), any()) } throws OrderNotFoundException("Order not found")

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)

        runTest {
            assertThrows<OrderNotFoundException> {
                cut.deleteOrder(HostName.AXIELL, "12345")
            }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.deleteOrder(any()) }
            coVerify(exactly = 0) { orderRepoMock.deleteOrder(any(), any()) }
        }
    }

    @Test
    fun `updateOrder with valid items should complete`() {
        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { orderRepoMock.getOrder(any(), any()) } answers { testOrder.copy() }
        coEvery { storageSystemRepoMock.updateOrder(any()) } answers { updatedOrder }
        coEvery { orderRepoMock.updateOrder(any()) } answers { updatedOrder }

        runTest {
            val order =
                cut.updateOrder(
                    HostName.AXIELL,
                    "12345",
                    listOf("mlt-420", "mlt-421"),
                    Order.Type.LOAN,
                    "unreal person",
                    createOrderAddress(),
                    "https://example.com"
                )

            assertThat(order).isEqualTo(updatedOrder)
            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 1) { storageSystemRepoMock.updateOrder(any()) }
            coVerify(exactly = 1) { orderRepoMock.updateOrder(any()) }
        }
    }

    @Test
    fun `updateOrder should fail when order does not exist`() {
        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)

        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { orderRepoMock.getOrder(any(), any()) } throws OrderNotFoundException("Order not found")

        runTest {
            assertThrows<OrderNotFoundException> {
                cut.updateOrder(
                    HostName.AXIELL,
                    "12345",
                    listOf("mlt-420", "mlt-421"),
                    testOrder.orderType,
                    testOrder.contactPerson,
                    testOrder.address ?: createOrderAddress(),
                    testOrder.callbackUrl
                )
            }
            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.updateOrder(any()) }
            coVerify(exactly = 0) { orderRepoMock.updateOrder(any()) }
        }
    }

    @Test
    fun `updateOrder should fail when items do not exist`() {
        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { false }

        runTest {
            assertThrows<ValidationException> {
                cut.updateOrder(
                    HostName.AXIELL,
                    "12345",
                    listOf("mlt-420", "mlt-421"),
                    testOrder.orderType,
                    testOrder.contactPerson,
                    testOrder.address,
                    testOrder.callbackUrl
                )
            }
            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 0) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.updateOrder(any()) }
            coVerify(exactly = 0) { orderRepoMock.updateOrder(any()) }
        }
    }

    @Test
    fun `getOrder should return requested order when it exists in DB`() {
        val expectedItem = testOrder.copy()

        coEvery { orderRepoMock.getOrder(HostName.AXIELL, "12345") } answers { expectedItem }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            val order = cut.getOrder(HostName.AXIELL, "12345")
            assertThat(order).isEqualTo(expectedItem)
        }
    }

    @Test
    fun `getOrder should return null when order does not exists in DB`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } answers { null }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock, inventoryNotifierMock)
        runTest {
            val order = cut.getOrder(HostName.AXIELL, "12345")
            assertThat(order).isNull()
        }
    }

    private val testItem =
        Item(
            hostName = HostName.AXIELL,
            hostId = "12345",
            description = "Tyven, tyven skal du hete",
            itemCategory = "BOOK",
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            owner = Owner.NB,
            callbackUrl = "https://callback.com/item",
            location = null,
            quantity = null
        )

    private val testOrder =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "12345",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(),
            orderType = Order.Type.LOAN,
            owner = Owner.NB,
            contactPerson = "contactPerson",
            address = createOrderAddress(),
            callbackUrl = "https://callback.com/order"
        )

    private val updatedOrder =
        testOrder.copy(
            orderLine =
                listOf(
                    Order.OrderItem("mlt-420", Order.OrderItem.Status.NOT_STARTED),
                    Order.OrderItem("mlt-421", Order.OrderItem.Status.NOT_STARTED)
                )
        )

    private val testMoveItemPayload =
        MoveItemPayload(
            "12345",
            HostName.AXIELL,
            1,
            "Somewhere nice"
        )

    private fun Order.toCreateOrderDTO() =
        CreateOrderDTO(
            hostName = testOrder.hostName,
            hostOrderId = testOrder.hostOrderId,
            orderLine = testOrder.orderLine.map { CreateOrderDTO.OrderItem(it.hostId) },
            orderType = testOrder.orderType,
            owner = testOrder.owner,
            contactPerson = testOrder.contactPerson,
            address = testOrder.address,
            callbackUrl = testOrder.callbackUrl
        )

    fun createOrderAddress(): Order.Address {
        return Order.Address(null, null, null, null, null, null)
    }
}
