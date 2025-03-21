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
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.outboxMessages.ItemCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OrderCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OrderDeleted
import no.nb.mlt.wls.domain.model.outboxMessages.OrderUpdated
import no.nb.mlt.wls.domain.model.outboxMessages.OutboxMessage
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import no.nb.mlt.wls.domain.ports.inbound.toOrder
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.OutboxMessageProcessor
import no.nb.mlt.wls.domain.ports.outbound.OutboxRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class WLSServiceTest {
    private val orderRepoMock = mockk<OrderRepository>()
    private val itemRepoMock = mockk<ItemRepository>()
    private val storageSystemRepoMock = mockk<StorageSystemFacade>()
    private val inventoryNotifierMock = mockk<InventoryNotifier>()
    private val emailAdapterMock = mockk<EmailNotifier>()
    private val outboxRepository = mockk<OutboxRepository>()
    private val transactionPort = mockk<TransactionPort>()
    private val outboxProcessor = mockk<OutboxMessageProcessor>()

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
    }

    @Test
    fun `addItem should save and return new item when it does not exists`() {
        val expectedItem = testItem.copy()
        val itemCreatedMessage = ItemCreated(expectedItem)
        coEvery { itemRepoMock.getItem(any(), any()) } answers { null }
        coEvery { itemRepoMock.createItem(any()) } answers { expectedItem }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } returns (expectedItem to itemCreatedMessage)
        coEvery { outboxProcessor.handleEvent(itemCreatedMessage) } answers {}
        coEvery { outboxRepository.save(itemCreatedMessage) } answers { itemCreatedMessage }

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
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
                        callbackUrl = testItem.callbackUrl
                    )
                )

            assertThat(itemResult).isEqualTo(expectedItem)
            coVerify(exactly = 1) { outboxProcessor.handleEvent(itemCreatedMessage) }
        }
    }

    @Test
    fun `addItem should not save new item but return existing item if it already exists`() {
        coEvery { itemRepoMock.getItem(testItem.hostName, testItem.hostId) } answers { testItem.copy() }
        coEvery { itemRepoMock.createItem(any()) } answers { testItem.copy() }
        coJustRun { storageSystemRepoMock.createItem(any()) }

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)

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
                        callbackUrl = testItem.callbackUrl
                    )
                )

            assertThat(newItem).isEqualTo(testItem)

            coVerify(exactly = 0) { itemRepoMock.createItem(any()) }
        }
    }

    @Test
    fun `getItem should return requested item when it exists in DB`() {
        val expectedItem = testItem.copy()

        coEvery { itemRepoMock.getItem(HostName.AXIELL, "12345") } answers { expectedItem }

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
        runTest {
            val itemResult = cut.getItem(HostName.AXIELL, "12345")
            assertThat(itemResult).isEqualTo(expectedItem)
        }
    }

    @Test
    fun `getItem should return null if item does not exist`() {
        coEvery { itemRepoMock.getItem(HostName.AXIELL, "12345") } answers { null }

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
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

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
        runTest {
            val movedItem = cut.moveItem(testMoveItemPayload)
            assertThat(movedItem).isEqualTo(expectedItem)

            coVerify(exactly = 1) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 1) { itemRepoMock.moveItem(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `moveItem should fail when item does not exist`() {
        coEvery { itemRepoMock.moveItem(any(), any(), any(), any()) } throws ItemNotFoundException("Item not found")

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
        runTest {
            assertThrows<RuntimeException> {
                cut.moveItem(testMoveItemPayload)
            }

            coVerify(exactly = 1) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepoMock.moveItem(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `moveItem throws when count is invalid`() {
        coEvery { itemRepoMock.moveItem(any(), any(), -1, any()) } throws ValidationException("Location cannot be blank")

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
        runTest {
            assertThrows<RuntimeException> {
                cut.moveItem(testMoveItemPayload.copy(quantity = -1))
            }

            coVerify(exactly = 0) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepoMock.moveItem(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `moveItem throws when location is blank`() {
        coEvery { itemRepoMock.moveItem(any(), any(), any(), any()) } throws ValidationException("Item not found")

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
        runTest {
            assertThrows<RuntimeException> {
                cut.moveItem(testMoveItemPayload.copy(location = "  "))
            }

            coVerify(exactly = 0) { itemRepoMock.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepoMock.moveItem(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `createOrder should save order in db and outbox`() {
        val expectedOrder = createOrderDTO.toOrder().copy()
        val orderCreatedMessage = OrderCreated(expectedOrder, UUID.randomUUID().toString())
        val transactionPortMock =
            object : TransactionPort {
                override suspend fun <T> executeInTransaction(action: suspend () -> T): T {
                    @Suppress("UNCHECKED_CAST")
                    return (expectedOrder to orderCreatedMessage) as T
                }
            }

        coEvery { orderRepoMock.getOrder(createOrderDTO.hostName, createOrderDTO.hostOrderId) } answers { null }
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { itemRepoMock.getItems(any(), any()) } answers { listOf() }
        coEvery { orderRepoMock.createOrder(createOrderDTO.toOrder()) } answers { expectedOrder }
        coEvery { outboxRepository.save(any() as OutboxMessage) } answers { orderCreatedMessage }
        coEvery { emailAdapterMock.orderCreated(any(), any()) } answers { }
        coEvery { outboxProcessor.handleEvent(orderCreatedMessage) } answers {}

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPortMock, outboxProcessor)
        runTest {
            val order = cut.createOrder(createOrderDTO)
            assertThat(order).isEqualTo(expectedOrder)
            coVerify(exactly = 1) { outboxProcessor.handleEvent(orderCreatedMessage) }
        }
    }

    @Test
    fun `createOrder should return existing order when trying to create one with same id and host`() {
        coEvery {
            orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId)
        } answers { testOrder.copy() }

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
        runTest {
            val order =
                cut.createOrder(
                    createOrderDTO.copy(callbackUrl = "https://new-callback-wls.no/order")
                )

            assertThat(order).isEqualTo(testOrder)
            assertThat(order.callbackUrl).isEqualTo(testOrder.callbackUrl)
            coVerify(exactly = 0) { orderRepoMock.createOrder(any()) }
        }
    }

    // Test create order when order items do not exist
    @Test
    fun `createOrder should fail if some of the items does not exist`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } answers { null }
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { false }

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
        runTest {
            assertThrows<ValidationException> {
                cut.createOrder(createOrderDTO)
            }
            coVerify(exactly = 0) { orderRepoMock.createOrder(any()) }
        }
    }

    @Test
    fun `deleteOrder should complete when order exists`() {
        val deletedOrderMessage = OrderDeleted(testOrder.hostName, testOrder.hostOrderId)
        coEvery { orderRepoMock.getOrder(any(), any()) } returns testOrder
        coJustRun { orderRepoMock.deleteOrder(any()) }
        coJustRun { storageSystemRepoMock.deleteOrder(any(), any()) }
        coEvery { transactionPort.executeInTransaction<Any>(any()) } returns deletedOrderMessage
        coEvery { outboxProcessor.handleEvent(deletedOrderMessage) } answers {}
        coEvery {
            outboxRepository.save(deletedOrderMessage)
        } answers { deletedOrderMessage }

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)

        runTest {
            cut.deleteOrder(HostName.AXIELL, "12345")
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
        }
    }

    @Test
    @Disabled
    fun `deleteOrder should fail when order does not exist in storage system`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } returns testOrder
        coEvery { storageSystemRepoMock.deleteOrder(any(), any()) } throws StorageSystemException("Order not found", null)

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)

        runTest {
            assertThrows<StorageSystemException> {
                cut.deleteOrder(HostName.AXIELL, "12345")
            }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 0) { orderRepoMock.deleteOrder(any()) }
        }
    }

    @Test
    fun `deleteOrder should fail when order does not exist in WLS database`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } throws OrderNotFoundException("Order not found")
        coJustRun { storageSystemRepoMock.deleteOrder(any(), any()) }
        coEvery { orderRepoMock.deleteOrder(any()) } throws OrderNotFoundException("Order not found")

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)

        runTest {
            assertThrows<OrderNotFoundException> {
                cut.deleteOrder(HostName.AXIELL, "12345")
            }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }

            coVerify(exactly = 0) { orderRepoMock.deleteOrder(any()) }
        }
    }

    @Test
    fun `updateOrder with valid items should complete`() {
        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { orderRepoMock.getOrder(any(), any()) } answers { testOrder.copy() }
        coEvery { storageSystemRepoMock.updateOrder(any()) } answers { updatedOrder }
        coEvery { orderRepoMock.updateOrder(any()) } answers { updatedOrder }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } returns (updatedOrder to OrderUpdated(updatedOrder))
        coEvery { outboxRepository.save(OrderUpdated(updatedOrder)) } answers { OrderUpdated(updatedOrder = updatedOrder) }

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
        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)

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
        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
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

    @Test
    fun `getOrder should return requested order when it exists in DB`() {
        val expectedItem = testOrder.copy()

        coEvery { orderRepoMock.getOrder(HostName.AXIELL, "12345") } answers { expectedItem }

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
        runTest {
            val order = cut.getOrder(HostName.AXIELL, "12345")
            assertThat(order).isEqualTo(expectedItem)
        }
    }

    @Test
    fun `getOrder should return null when order does not exists in DB`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } answers { null }

        val cut = WLSService(itemRepoMock, orderRepoMock, inventoryNotifierMock, outboxRepository, transactionPort, outboxProcessor)
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
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            callbackUrl = "https://callback-wls.no/item",
            location = "UNKNOWN",
            quantity = 0
        )

    private val testOrder =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "12345",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(),
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
}
