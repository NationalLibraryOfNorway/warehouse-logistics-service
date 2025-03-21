package no.nb.mlt.wls.domain

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.catalogEvents.CatalogEvent
import no.nb.mlt.wls.domain.model.catalogEvents.ItemEvent
import no.nb.mlt.wls.domain.model.catalogEvents.OrderEvent
import no.nb.mlt.wls.domain.model.storageEvents.ItemCreated
import no.nb.mlt.wls.domain.model.storageEvents.OrderCreated
import no.nb.mlt.wls.domain.model.storageEvents.OrderDeleted
import no.nb.mlt.wls.domain.model.storageEvents.OrderUpdated
import no.nb.mlt.wls.domain.model.storageEvents.StorageEvent
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WLSServiceTest {
    private val itemRepoMock = mockk<ItemRepository>()
    private val orderRepoMock = mockk<OrderRepository>()
    private val catalogEventRepository = mockk<EventRepository<CatalogEvent>>()
    private val storageEventRepository = mockk<EventRepository<StorageEvent>>()
    private val transactionPort = mockk<TransactionPort>()
    private val catalogEventProcessor = mockk<EventProcessor<CatalogEvent>>()
    private val storageEventProcessor = mockk<EventProcessor<StorageEvent>>()

    private val storageSystemRepoMock = mockk<StorageSystemFacade>()
    private val emailAdapterMock = mockk<EmailNotifier>()

    private lateinit var cut: WLSService

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()

        cut = WLSService(itemRepoMock, orderRepoMock, catalogEventRepository, storageEventRepository, transactionPort, catalogEventProcessor, storageEventProcessor)
    }

    @Test
    fun `addItem should save and return new item when it does not exists`() {
        val itemCreatedEvent = ItemCreated(testItem)
        coEvery { itemRepoMock.getItem(testItem.hostName, testItem.hostId) } answers { null }
        coEvery { itemRepoMock.createItem(testItem) } answers { testItem }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } returns (testItem to itemCreatedEvent)
        coEvery { storageEventProcessor.handleEvent(itemCreatedEvent) } answers {}

        runTest {
            val itemResult = cut.addItem(testItem.toItemMetadata())

            assertThat(itemResult).isEqualTo(testItem)
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(itemCreatedEvent) }
        }
    }

    @Test
    fun `addItem should not save new item but return existing item if it already exists`() {
        coEvery { itemRepoMock.getItem(testItem.hostName, testItem.hostId) } answers { testItem }
        coEvery { itemRepoMock.createItem(testItem) } answers { testItem }
        coJustRun { storageSystemRepoMock.createItem(any()) }

        runTest {
            val itemResult = cut.addItem(testItem.toItemMetadata())

            assertThat(itemResult).isEqualTo(testItem)
            coVerify(exactly = 0) { itemRepoMock.createItem(any()) }
            coVerify(exactly = 0) { storageEventProcessor.handleEvent(any()) }
        }
    }

    @Test
    fun `moveItem should return when item successfully moves`() {
        val expectedItem = testItem.copy(location = testMoveItemPayload.location, quantity = testMoveItemPayload.quantity)
        val itemMovedEvent = ItemEvent(expectedItem)
        coEvery { itemRepoMock.getItem(testItem.hostName, testItem.hostId) } answers { testItem }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } returns (expectedItem to itemMovedEvent)
        coEvery { catalogEventProcessor.handleEvent(itemMovedEvent) } answers {}

        runTest {
            val movedItem = cut.moveItem(testMoveItemPayload)
            assertThat(movedItem).isEqualTo(expectedItem)

            coVerify(exactly = 1) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(itemMovedEvent) }
        }
    }

    @Test
    fun `moveItem should fail when item does not exist`() {
        coEvery { itemRepoMock.getItem(testItem.hostName, testItem.hostId) } answers { null }

        runTest {
            assertThrows<ItemNotFoundException> {
                cut.moveItem(testMoveItemPayload)
            }

            coVerify(exactly = 1) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepoMock.moveItem(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `moveItem throws when count is invalid`() {
        runTest {
            assertThrows<ValidationException>(message = "Quantity can not be negative") {
                cut.moveItem(testMoveItemPayload.copy(quantity = -1))
            }

            coVerify(exactly = 0) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepoMock.moveItem(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `moveItem throws when location is blank`() {
        runTest {
            assertThrows<ValidationException>(message = "Location can not be blank") {
                cut.moveItem(testMoveItemPayload.copy(location = "  "))
            }

            coVerify(exactly = 0) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepoMock.moveItem(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `pickItems should update items and send callbacks`() {
        val expectedItem = testItem.copy(quantity = 0, location = "WITH_LENDER")
        val pickedItemsMap = mapOf(testItem.hostId to 1)
        val itemPickedEvent = ItemEvent(expectedItem)
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { itemRepoMock.getItems(any(), any()) } answers { listOf(testItem) }
        coEvery { transactionPort.executeInTransaction<Any>(any()) } answers { itemPickedEvent }
        coEvery { catalogEventProcessor.handleEvent(itemPickedEvent) } answers {}

        runTest {
            cut.pickItems(testItem.hostName, pickedItemsMap)

            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 1) { itemRepoMock.getItems(any(), any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(itemPickedEvent) }
        }
    }

    @Test
    fun `pickItems throw if item does not exist`() {
        val pickedItemsMap = mapOf(testItem.hostId to 1)
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { false }

        runTest {
            assertThrows<ItemNotFoundException>(message = "Some items do not exist in the database, and were unable to be picked") {
                cut.pickItems(testItem.hostName, pickedItemsMap)
            }

            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 0) { itemRepoMock.getItems(any(), any()) }
        }
    }

    @Test
    fun `pickOrderItems should update order and send callback`() {
        val expectedOrder = testOrder.copy(orderLine = listOf(Order.OrderItem(testItem.hostId, Order.OrderItem.Status.PICKED)))
        val pickedItems = listOf(testItem.hostId)
        val orderItemsPickedEvent = OrderEvent(expectedOrder)
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { transactionPort.executeInTransaction<Any>(any()) } answers { orderItemsPickedEvent }
        coEvery { catalogEventProcessor.handleEvent(orderItemsPickedEvent) } answers {}

        runTest {
            cut.pickOrderItems(testOrder.hostName, pickedItems, testOrder.hostOrderId)

            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(orderItemsPickedEvent) }
        }
    }

    @Test
    fun `createOrder should save order in db and outbox`() {
        val orderCreatedEvent = OrderCreated(testOrder)
        coEvery { orderRepoMock.getOrder(createOrderDTO.hostName, createOrderDTO.hostOrderId) } answers { null }
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } answers { testOrder to orderCreatedEvent }
        coEvery { storageEventProcessor.handleEvent(orderCreatedEvent) } answers {}

        runTest {
            val order = cut.createOrder(createOrderDTO)
            assertThat(order).isEqualTo(testOrder)
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(orderCreatedEvent) }
        }
    }

    @Test
    fun `createOrder should return existing order when trying to create one with same id and host`() {
        val duplicateOrder = createOrderDTO.copy(callbackUrl = "https://new-callback-wls.no/order")
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }

        runTest {
            val order = cut.createOrder(duplicateOrder)

            assertThat(order).isEqualTo(testOrder)
            assertThat(order.callbackUrl).isEqualTo(testOrder.callbackUrl)
            coVerify(exactly = 0) { itemRepoMock.doesEveryItemExist(any()) }
        }
    }

    @Test
    fun `createOrder should fail if some of the items does not exist`() {
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { false }

        runTest {
            assertThrows<ValidationException>(message = "All order items in order must exist") {
                cut.createOrder(createOrderDTO)
            }
            coVerify(exactly = 0) { orderRepoMock.createOrder(any()) }
        }
    }

    @Test
    fun `deleteOrder should complete when order exists`() {
        val deletedOrderEvent = OrderDeleted(testOrder.hostName, testOrder.hostOrderId)
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { transactionPort.executeInTransaction<Any>(any()) } answers { deletedOrderEvent }
        coEvery { storageEventProcessor.handleEvent(deletedOrderEvent) } answers {}

        runTest {
            cut.deleteOrder(testOrder.hostName, testOrder.hostOrderId)
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify { storageEventProcessor.handleEvent(deletedOrderEvent) }
        }
    }

    @Test
    fun `deleteOrder should fail when order does not exist in WLS DB`() {
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }

        runTest {
            assertThrows<OrderNotFoundException>(message = "No order with hostOrderId: $testOrder.hostOrderId and hostName: $testOrder.hostName exists") {
                cut.deleteOrder(testOrder.hostName, testOrder.hostOrderId)
            }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 0) { transactionPort.executeInTransaction<Any>(any()) }
        }
    }

    @Test
    fun `deleteOrder should fail when order deletion fails`() {
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { storageSystemRepoMock.deleteOrder(any(), any()) } throws StorageSystemException("Order not found", null)

        runTest {
            assertThrows<RuntimeException>(message = "Could not delete order") {
                cut.deleteOrder(testOrder.hostName, testOrder.hostOrderId)
            }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 1) { transactionPort.executeInTransaction<Any>(any()) }
        }
    }

    @Test
    fun `updateOrder with valid items should complete`() {
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { orderRepoMock.getOrder(any(), any()) } answers { testOrder.copy() }
        coEvery { storageSystemRepoMock.updateOrder(any()) } answers { updatedOrder }
        coEvery { orderRepoMock.updateOrder(any()) } answers { updatedOrder }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } returns (updatedOrder to OrderUpdated(updatedOrder))
        coEvery { storageEventRepository.save(OrderUpdated(updatedOrder)) } answers { OrderUpdated(updatedOrder = updatedOrder) }

        runTest {
            val order =
                cut.updateOrder(
                    HostName.AXIELL,
                    "12345",
                    listOf("mlt-420", "mlt-421"),
                    Order.Type.LOAN,
                    "unreal person",
                    createOrderAddress(),
                    "note",
                    "https://example.com"
                )

            assertThat(order).isEqualTo(updatedOrder)
            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
        }
    }

    @Test
    fun `updateOrder should fail when order does not exist`() {
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
                    testOrder.note,
                    testOrder.callbackUrl
                )
            }
            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }

            coVerify(exactly = 0) { orderRepoMock.updateOrder(any()) }
        }
    }

    @Test
    fun `updateOrder should fail when items do not exist`() {
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
                    testOrder.note,
                    testOrder.callbackUrl
                )
            }
            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 0) { orderRepoMock.getOrder(any(), any()) }

            coVerify(exactly = 0) { orderRepoMock.updateOrder(any()) }
        }
    }

    //TODO test UpdateOrderStatus

    @Test
    fun `getItem should return requested item when it exists in DB`() {
        coEvery { itemRepoMock.getItem(testItem.hostName, testItem.hostId) } answers { testItem }

        runTest {
            val itemResult = cut.getItem(testItem.hostName, testItem.hostId)
            assertThat(itemResult).isEqualTo(testItem)
        }
    }

    @Test
    fun `getItem should return null if item does not exist`() {
        coEvery { itemRepoMock.getItem(testItem.hostName, testItem.hostId) } answers { null }

        runTest {
            val itemResult = cut.getItem(testItem.hostName, testItem.hostId)
            assertThat(itemResult).isNull()
        }
    }

    @Test
    fun `getOrder should return requested order when it exists in DB`() {
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }

        runTest {
            val orderResult = cut.getOrder(testOrder.hostName, testOrder.hostOrderId)
            assertThat(orderResult).isEqualTo(testOrder)
        }
    }

    @Test
    fun `getOrder should return null when order does not exists in DB`() {
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }

        runTest {
            val orderResult = cut.getOrder(testOrder.hostName, testOrder.hostOrderId)
            assertThat(orderResult).isNull()
        }
    }

    private val testItem =
        Item(
            hostName = HostName.AXIELL,
            hostId = "mlt-12345",
            description = "Tyven, tyven skal du hete",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            callbackUrl = "https://callback-wls.no/item",
            location = "UNKNOWN",
            quantity = 0
        )

    private val testMoveItemPayload =
        MoveItemPayload(
            hostName = testItem.hostName,
            hostId =  testItem.hostId,
            quantity = 1,
            location = "KNOWN_LOCATION"
        )

    private val testOrder =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "mlt-12345-order",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(Order.OrderItem(testItem.hostId, Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            contactPerson = "contactPerson",
            contactEmail = "contact@ema.il",
            address = createOrderAddress(),
            note = "note",
            callbackUrl = "https://callback-wls.no/order"
        )

    private val updatedOrder =
        testOrder.copy(
            orderLine =
                listOf(
                    Order.OrderItem("testItem.hostId", Order.OrderItem.Status.NOT_STARTED),
                    Order.OrderItem("mlt-54321", Order.OrderItem.Status.NOT_STARTED)
                )
        )

    private val createOrderDTO =
        CreateOrderDTO(
            hostName = testOrder.hostName,
            hostOrderId = testOrder.hostOrderId,
            orderLine = testOrder.orderLine.map { CreateOrderDTO.OrderItem(it.hostId) },
            orderType = testOrder.orderType,
            contactPerson = testOrder.contactPerson,
            contactEmail = testOrder.contactEmail,
            address = testOrder.address,
            note = testOrder.note,
            callbackUrl = testOrder.callbackUrl
        )

    private fun createOrderAddress(): Order.Address {
        return Order.Address(null, null, null, null, null, null, null)
    }

    private fun Item.toItemMetadata() = ItemMetadata(
        hostId = hostId,
        hostName = hostName,
        description = description,
        itemCategory = itemCategory,
        preferredEnvironment = preferredEnvironment,
        packaging = packaging,
        callbackUrl = callbackUrl
    )
}
