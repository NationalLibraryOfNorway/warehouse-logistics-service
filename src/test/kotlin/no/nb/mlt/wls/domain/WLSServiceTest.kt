package no.nb.mlt.wls.domain

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION
import no.nb.mlt.wls.domain.model.WITH_LENDER_LOCATION
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem.UpdateItemPayload
import no.nb.mlt.wls.domain.ports.inbound.exceptions.IllegalOrderStateException
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.exceptions.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import no.nb.mlt.wls.domain.ports.outbound.exceptions.StorageSystemException
import no.nb.mlt.wls.toItemMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WLSServiceTest {
    private val itemRepository = mockk<ItemRepository>()
    private val orderRepository = mockk<OrderRepository>()
    private val catalogEventRepository = mockk<EventRepository<CatalogEvent>>()
    private val storageEventRepository = mockk<EventRepository<StorageEvent>>()
    private val catalogEventProcessor = mockk<EventProcessor<CatalogEvent>>()
    private val storageEventProcessor = mockk<EventProcessor<StorageEvent>>()
    private val storageSystemRepoMock = mockk<StorageSystemFacade>()
    private val transactionPortMock = mockk<TransactionPort>()
    private val transactionPortExecutor =
        object : TransactionPort {
            override suspend fun <T> executeInTransaction(action: suspend () -> T): T = action()
        }

    private lateinit var serviceSansTrans: WLSService
    private lateinit var serviceAvecTrans: WLSService

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()

        serviceSansTrans =
            WLSService(
                itemRepository,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortMock,
                catalogEventProcessor,
                storageEventProcessor
            )

        serviceAvecTrans =
            WLSService(
                itemRepository,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )
    }

    //
    // // Regular/Simple Tests
    //

    @Test
    fun `addItem should save and return new item when it does not exists`() {
        val expectedItem = createTestItem()
        val itemCreatedEvent = ItemCreated(expectedItem)

        coEvery { itemRepository.getItem(expectedItem.hostName, expectedItem.hostId) } answers { null }
        coEvery { itemRepository.createItem(expectedItem) } answers { expectedItem }
        coEvery { storageEventRepository.save(any()) } answers { itemCreatedEvent }
        coEvery { storageEventProcessor.handleEvent(itemCreatedEvent) } answers {}

        runTest {
            val addItemResult = serviceAvecTrans.addItem(expectedItem.toItemMetadata())

            assertThat(addItemResult).isEqualTo(expectedItem)
            coVerify(exactly = 1) { itemRepository.createItem(expectedItem) }
            coVerify(exactly = 1) { storageEventRepository.save(any()) }
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(itemCreatedEvent) }
        }
    }

    @Test
    fun `addItem should not save new item but return existing item if it already exists`() {
        val expectedItem = createTestItem()

        coEvery { itemRepository.getItem(expectedItem.hostName, expectedItem.hostId) } answers { expectedItem }
        coEvery { itemRepository.createItem(expectedItem) } answers { expectedItem }
        coJustRun { storageSystemRepoMock.createItem(any()) }

        runTest {
            val addItemResult = serviceSansTrans.addItem(expectedItem.toItemMetadata())

            assertThat(addItemResult).isEqualTo(expectedItem)
            coVerify(exactly = 0) { itemRepository.createItem(any()) }
            coVerify(exactly = 0) { storageEventProcessor.handleEvent(any()) }
        }
    }

    @Test
    fun `updateItem should change item's location and quantity`() {
        val startingItem = createTestItem(location = "SYNQ_WAREHOUSE", quantity = 1)
        val expectedItem =
            createTestItem(
                hostName = startingItem.hostName,
                hostId = startingItem.hostId,
                location = WITH_LENDER_LOCATION,
                associatedStorage = AssociatedStorage.SYNQ,
                quantity = 0
            )
        val updateItemPayload =
            UpdateItemPayload(
                expectedItem.hostName,
                expectedItem.hostId,
                expectedItem.quantity,
                expectedItem.location,
                expectedItem.associatedStorage
            )
        val itemUpdatedEvent = ItemEvent(expectedItem)

        coEvery { itemRepository.getItem(startingItem.hostName, startingItem.hostId) } answers { startingItem }
        coEvery {
            itemRepository.updateItem(
                startingItem.hostId,
                startingItem.hostName,
                expectedItem.quantity,
                expectedItem.location,
                expectedItem.associatedStorage
            )
        } answers { expectedItem }
        coEvery { catalogEventRepository.save(any()) } answers { itemUpdatedEvent }
        coEvery { catalogEventProcessor.handleEvent(itemUpdatedEvent) } answers {}

        runTest {
            val updateItemResult = serviceAvecTrans.updateItem(updateItemPayload)

            assertThat(updateItemResult).isEqualTo(expectedItem)
            coVerify(exactly = 1) { itemRepository.updateItem(any(), any(), any(), any(), any()) }
            coVerify(exactly = 1) { catalogEventRepository.save(any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(itemUpdatedEvent) }
        }
    }

    @Test
    fun `moveItem should change item's location and quantity`() {
        val startingItem = createTestItem(location = "SYNQ_WAREHOUSE", quantity = 1)
        val expectedItem =
            createTestItem(hostName = startingItem.hostName, hostId = startingItem.hostId, location = WITH_LENDER_LOCATION, quantity = -1)
        val moveItemPayload =
            MoveItemPayload(expectedItem.hostName, expectedItem.hostId, expectedItem.quantity, expectedItem.location, expectedItem.associatedStorage)
        val itemMovedEvent = ItemEvent(expectedItem)

        coEvery { itemRepository.getItem(startingItem.hostName, startingItem.hostId) } answers { startingItem }
        coEvery {
            itemRepository.moveItem(
                startingItem.hostName,
                startingItem.hostId,
                startingItem.quantity + expectedItem.quantity,
                expectedItem.location,
                expectedItem.associatedStorage
            )
        } answers { expectedItem }
        coEvery { catalogEventRepository.save(any()) } answers { itemMovedEvent }
        coEvery { catalogEventProcessor.handleEvent(itemMovedEvent) } answers {}

        runTest {
            val moveItemResult = serviceAvecTrans.moveItem(moveItemPayload)

            assertThat(moveItemResult).isEqualTo(expectedItem)
            coVerify(exactly = 1) { itemRepository.moveItem(any(), any(), any(), any(), any()) }
            coVerify(exactly = 1) { catalogEventRepository.save(any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(itemMovedEvent) }
        }
    }

    @Test
    fun `moveItem should fail when item does not exist`() {
        coEvery { itemRepository.getItem(testItem.hostName, testItem.hostId) } answers { null }

        runTest {
            assertThrows<ItemNotFoundException> {
                serviceSansTrans.moveItem(testMoveItemPayload)
            }

            coVerify(exactly = 1) { itemRepository.getItem(any(), any()) }
            coVerify(exactly = 0) { itemRepository.moveItem(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `pickItems should update items and send callbacks`() {
        val expectedItem = createTestItem(quantity = 0, location = WITH_LENDER_LOCATION)
        val pickedItemsMap = mapOf(expectedItem.hostId to 1)
        val itemPickedEvent = ItemEvent(expectedItem)

        coEvery { itemRepository.doesEveryItemExist(any()) } answers { true }
        coEvery { itemRepository.getItemsByIds(any(), any()) } answers { listOf(expectedItem) }
        coEvery {
            itemRepository.moveItem(
                expectedItem.hostName,
                expectedItem.hostId,
                expectedItem.quantity,
                expectedItem.location,
                expectedItem.associatedStorage
            )
        } answers { expectedItem }
        coEvery { catalogEventRepository.save(any()) } answers { itemPickedEvent }
        coEvery { catalogEventProcessor.handleEvent(itemPickedEvent) } answers {}

        runTest {
            serviceAvecTrans.pickItems(expectedItem.hostName, pickedItemsMap)

            coVerify(exactly = 1) { itemRepository.doesEveryItemExist(any()) }
            coVerify(exactly = 1) { itemRepository.getItemsByIds(any(), any()) }
            coVerify(exactly = 1) { itemRepository.moveItem(any(), any(), any(), any(), any()) }
            coVerify(exactly = 1) { catalogEventRepository.save(any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(itemPickedEvent) }
        }
    }

    @Test
    fun `pickItems throw if item does not exist`() {
        val pickedItemsMap = mapOf(testItem.hostId to 1)

        coEvery { itemRepository.doesEveryItemExist(any()) } answers { false }

        runTest {
            assertThrows<ItemNotFoundException> {
                serviceSansTrans.pickItems(testItem.hostName, pickedItemsMap)
            }

            coVerify(exactly = 1) { itemRepository.doesEveryItemExist(any()) }
            coVerify(exactly = 0) { itemRepository.getItemsByIds(any(), any()) }
        }
    }

    @Test
    fun `createOrder should create order and send callback`() {
        val orderCreatedEvent = OrderCreated(testOrder)

        coEvery { orderRepository.getOrder(createOrderDTO.hostName, createOrderDTO.hostOrderId) } answers { null }
        coEvery { itemRepository.getItemsByIds(createOrderDTO.hostName, createOrderDTO.orderLine.map { it.hostId }) } answers { testOrderItems }
        coEvery { orderRepository.createOrder(createOrderDTO.toOrder()) } answers { testOrder }
        coEvery { storageEventRepository.save(any()) } answers { orderCreatedEvent }
        coEvery { storageEventProcessor.handleEvent(orderCreatedEvent) } answers {}

        runTest {
            val createOrderResult = serviceAvecTrans.createOrder(createOrderDTO)
            assertThat(createOrderResult).isEqualTo(testOrder)

            coVerify(exactly = 1) { orderRepository.createOrder(createOrderDTO.toOrder()) }
            coVerify(exactly = 1) { storageEventRepository.save(any()) }
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(orderCreatedEvent) }
        }
    }

    @Test
    fun `createOrder should return existing order when trying to create one with same id and host`() {
        val duplicateOrder = createOrderDTO.copy(note = "Other order", contactEmail = "other@ema.il", contactPerson = "Other Person")
        coEvery { orderRepository.getOrder(duplicateOrder.hostName, duplicateOrder.hostOrderId) } answers { testOrder }

        runTest {
            val createOrderResult = serviceSansTrans.createOrder(duplicateOrder)

            assertThat(createOrderResult).isEqualTo(testOrder)
            assertThat(createOrderResult.callbackUrl).isEqualTo(testOrder.callbackUrl)
            coVerify(exactly = 0) { itemRepository.doesEveryItemExist(any()) }
        }
    }

    @Test
    fun `createOrder handles missing items`() {
        val orderCreatedEvent = OrderCreated(testOrder)
        val missingItem =
            createTestItem(
                hostName = testOrder.hostName,
                hostId = testOrder.orderLine[1].hostId,
                description = "NO DESCRIPTION",
                itemCategory = ItemCategory.UNKNOWN,
                preferredEnvironment = Environment.NONE,
                packaging = Packaging.UNKNOWN,
                callbackUrl = null,
                location = UNKNOWN_LOCATION,
                quantity = 0
            )
        val itemCreatedEvent = ItemCreated(missingItem)

        coEvery { orderRepository.getOrder(createOrderDTO.hostName, createOrderDTO.hostOrderId) } answers { null }
        coEvery { itemRepository.getItemsByIds(createOrderDTO.hostName, createOrderDTO.orderLine.map { it.hostId }) } answers { listOf(testItem) }
        coEvery { orderRepository.createOrder(createOrderDTO.toOrder()) } answers { testOrder }
        coEvery { storageEventProcessor.handleEvent(orderCreatedEvent) } answers {}
        // Needed for creating missing item
        coEvery { storageEventRepository.save(any()) } returnsMany (listOf(itemCreatedEvent, orderCreatedEvent))
        coEvery { itemRepository.getItem(missingItem.hostName, missingItem.hostId) } answers { null }
        coEvery { itemRepository.createItem(missingItem) } answers { missingItem }

        runTest {
            val createOrderResult = serviceAvecTrans.createOrder(createOrderDTO)
            assertThat(createOrderResult).isEqualTo(testOrder)

            coVerify(exactly = 1) { orderRepository.createOrder(createOrderDTO.toOrder()) }
            coVerify(exactly = 2) { storageEventRepository.save(any()) }
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(itemCreatedEvent) }
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(orderCreatedEvent) }
        }
    }

    @Test
    fun `pickOrderItems should update order and send callback`() {
        val expectedItem = createTestItem()
        val expectedOrder =
            testOrder.copy(
                status = Order.Status.IN_PROGRESS,
                orderLine = listOf(Order.OrderItem(expectedItem.hostId, Order.OrderItem.Status.PICKED), testOrder.orderLine[1])
            )
        val pickedItems = listOf(expectedItem.hostId)
        val orderItemsPickedEvent = OrderEvent(expectedOrder)

        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { orderRepository.updateOrder(expectedOrder) } answers { expectedOrder }
        coEvery { catalogEventRepository.save(any()) } answers { orderItemsPickedEvent }
        coEvery { catalogEventProcessor.handleEvent(orderItemsPickedEvent) } answers {}

        runTest {
            serviceAvecTrans.pickOrderItems(testOrder.hostName, pickedItems, testOrder.hostOrderId)

            coVerify(exactly = 1) { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) }
            coVerify(exactly = 1) { orderRepository.updateOrder(expectedOrder) }
            coVerify(exactly = 1) { catalogEventRepository.save(any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(orderItemsPickedEvent) }
        }
    }

    @Test
    fun `deleteOrder should complete when order exists`() {
        val deletedOrder = testOrder.copy(status = Order.Status.DELETED)
        val deletedOrderEvent = OrderDeleted(testOrder.hostName, testOrder.hostOrderId)

        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { orderRepository.deleteOrder(deletedOrder) } answers {}
        coEvery { storageEventRepository.save(any()) } answers { deletedOrderEvent }
        coEvery { storageEventProcessor.handleEvent(deletedOrderEvent) } answers {}

        runTest {
            serviceAvecTrans.deleteOrder(testOrder.hostName, testOrder.hostOrderId)

            coVerify(exactly = 1) { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) }
            coVerify(exactly = 1) { orderRepository.deleteOrder(deletedOrder) }
            coVerify(exactly = 1) { storageEventRepository.save(any()) }
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(deletedOrderEvent) }
        }
    }

    @Test
    fun `deleteOrder should fail when order does not exist in WLS DB`() {
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }

        runTest {
            assertThrows<OrderNotFoundException> {
                serviceSansTrans.deleteOrder(testOrder.hostName, testOrder.hostOrderId)
            }
            coVerify(exactly = 1) { orderRepository.getOrder(any(), any()) }
            coVerify(exactly = 0) { transactionPortMock.executeInTransaction<Any>(any()) }
        }
    }

    @Test
    fun `deleteOrder should fail when order deletion fails`() {
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { storageSystemRepoMock.deleteOrder(any(), any()) } throws StorageSystemException("Order not found", null)

        runTest {
            assertThrows<RuntimeException> {
                serviceSansTrans.deleteOrder(testOrder.hostName, testOrder.hostOrderId)
            }
            coVerify(exactly = 1) { orderRepository.getOrder(any(), any()) }
            coVerify(exactly = 1) { transactionPortMock.executeInTransaction<Any>(any()) }
        }
    }

    @Test
    fun `updateOrderStatus should update status and send callback`() {
        val completedOrder = testOrder.copy(status = Order.Status.COMPLETED)
        val orderStatusUpdatedEvent = OrderEvent(completedOrder)

        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { orderRepository.updateOrder(completedOrder) } answers { completedOrder }
        coEvery { catalogEventRepository.save(any()) } answers { orderStatusUpdatedEvent }
        coEvery { catalogEventProcessor.handleEvent(orderStatusUpdatedEvent) } answers { }

        runTest {
            val updateOrderStatusResult = serviceAvecTrans.updateOrderStatus(testOrder.hostName, testOrder.hostOrderId, Order.Status.COMPLETED)

            assertThat(updateOrderStatusResult).isEqualTo(completedOrder)
            coVerify(exactly = 1) { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) }
            coVerify(exactly = 1) { orderRepository.updateOrder(completedOrder) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(orderStatusUpdatedEvent) }
        }
    }

    @Test
    fun `updateOrderStatus should fail if order does not exist`() {
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }

        runTest {
            assertThrows<OrderNotFoundException> {
                serviceSansTrans.updateOrderStatus(testOrder.hostName, testOrder.hostOrderId, Order.Status.COMPLETED)
            }
            coVerify(exactly = 1) { orderRepository.getOrder(any(), any()) }
            coVerify(exactly = 0) { catalogEventProcessor.handleEvent(any()) }
        }
    }

    @Test
    fun `updateOrderStatus should fail if status transition is invalid`() {
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }

        runTest {
            assertThrows<IllegalOrderStateException> {
                serviceAvecTrans.updateOrderStatus(testOrder.hostName, testOrder.hostOrderId, Order.Status.NOT_STARTED)
            }
            coVerify(exactly = 1) { orderRepository.getOrder(any(), any()) }
            coVerify(exactly = 0) { catalogEventProcessor.handleEvent(any()) }
        }
    }

    @Test
    fun `getItem should return requested item when it exists in DB`() {
        coEvery { itemRepository.getItem(testItem.hostName, testItem.hostId) } answers { testItem }

        runTest {
            val itemResult = serviceSansTrans.getItem(testItem.hostName, testItem.hostId)

            assertThat(itemResult).isEqualTo(testItem)
        }
    }

    @Test
    fun `getItem should return null if item does not exist`() {
        coEvery { itemRepository.getItem(testItem.hostName, testItem.hostId) } answers { null }

        runTest {
            val itemResult = serviceSansTrans.getItem(testItem.hostName, testItem.hostId)

            assertThat(itemResult).isNull()
        }
    }

    @Test
    fun `getAllItems should return items for requested hosts`() {
        coEvery { itemRepository.getAllItemsForHosts(listOf(testItem.hostName)) } answers { listOf(testItem) }

        runTest {
            val itemListResult = serviceSansTrans.getAllItems(listOf(testItem.hostName))

            assertThat(itemListResult).isEqualTo(listOf(testItem))
        }
    }

    @Test
    fun `getAllItems should return empty list if no items exist for given host`() {
        coEvery { itemRepository.getAllItemsForHosts(listOf(HostName.MAVIS)) } answers { listOf() }

        runTest {
            val itemListResult = serviceSansTrans.getAllItems(listOf(HostName.MAVIS))

            assertThat(itemListResult).isEmpty()
        }
    }

    @Test
    fun `getOrder should return requested order when it exists in DB`() {
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }

        runTest {
            val orderResult = serviceSansTrans.getOrder(testOrder.hostName, testOrder.hostOrderId)

            assertThat(orderResult).isEqualTo(testOrder)
        }
    }

    @Test
    fun `getOrder should return null when order does not exists in DB`() {
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }

        runTest {
            val orderResult = serviceSansTrans.getOrder(testOrder.hostName, testOrder.hostOrderId)

            assertThat(orderResult).isNull()
        }
    }

    @Test
    fun `getAllOrders should return orders for requested hosts`() {
        coEvery { orderRepository.getAllOrdersForHosts(listOf(testOrder.hostName)) } answers { listOf(testOrder) }

        runTest {
            val orderListResult = serviceSansTrans.getAllOrders(listOf(testOrder.hostName))

            assertThat(orderListResult).isEqualTo(listOf(testOrder))
        }
    }

    @Test
    fun `getAllOrders should return empty list if no getAllOrders exist for given host`() {
        coEvery { orderRepository.getAllOrdersForHosts(listOf(HostName.MAVIS)) } answers { listOf() }

        runTest {
            val orderListResult = serviceSansTrans.getAllOrders(listOf(HostName.MAVIS))

            assertThat(orderListResult).isEmpty()
        }
    }

    //
    // // Mock Integration Tests
    //

    @Test
    fun `createOrder should save order in db and outbox`() {
        val itemRepository = createInMemItemRepo(testOrderItems.toMutableList())
        val orderCreatedEvent = OrderCreated(testOrder)
        val cut =
            WLSService(
                itemRepository,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        coEvery { orderRepository.getOrder(createOrderDTO.hostName, createOrderDTO.hostOrderId) } answers { null }
        coEvery { orderRepository.createOrder(testOrder) } answers { testOrder }
        coEvery { storageEventRepository.save(any()) } answers { orderCreatedEvent }
        coEvery { storageEventProcessor.handleEvent(orderCreatedEvent) } answers {}

        runTest {
            val createOrderResult = cut.createOrder(createOrderDTO)

            assertThat(createOrderResult).isEqualTo(testOrder)
            coVerify(exactly = 1) { orderRepository.createOrder(testOrder) }
            coVerify(exactly = 1) { storageEventRepository.save(any()) }
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(orderCreatedEvent) }
        }
    }

    @Test
    fun `createOrder should create items with unknown properties if they do not exist`() {
        val itemRepository = createInMemItemRepo(mutableListOf(testOrderItems[0]))
        val cut =
            WLSService(
                itemRepository,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }
        coEvery { orderRepository.createOrder(testOrder) } answers { testOrder }
        coEvery { catalogEventProcessor.handleEvent(any()) } answers { }
        coEvery { storageEventRepository.save(any()) } answers { OrderCreated(testOrder) }
        coEvery { storageEventProcessor.handleEvent(any()) } answers { }

        runTest {
            cut.createOrder(createOrderDTO)

            assert(itemRepository.doesEveryItemExist(testOrder.orderLine.map { ItemRepository.ItemId(testOrder.hostName, it.hostId) }))
            assertThat(itemRepository.getItem(testOrderItems[1].hostName, testOrderItems[1].hostId)).isNotNull
        }
    }

    @Test
    fun `pickOrderItems with duplicate lines should only generate one event`() {
        val pickedItem = createTestItem(location = WITH_LENDER_LOCATION, quantity = 0)
        val order =
            createTestOrder(
                orderLine =
                    listOf(
                        Order.OrderItem(testItem.hostId, Order.OrderItem.Status.NOT_STARTED),
                        Order.OrderItem(testItem.hostId, Order.OrderItem.Status.NOT_STARTED),
                        Order.OrderItem(testItem.hostId, Order.OrderItem.Status.NOT_STARTED),
                        Order.OrderItem(testItem.hostId, Order.OrderItem.Status.NOT_STARTED),
                        Order.OrderItem(testItem.hostId, Order.OrderItem.Status.NOT_STARTED),
                        Order.OrderItem("testItem2", Order.OrderItem.Status.NOT_STARTED)
                    )
            )
        val expectedOrder =
            order.copy(
                status = Order.Status.IN_PROGRESS,
                orderLine =
                    listOf(
                        Order.OrderItem(pickedItem.hostId, Order.OrderItem.Status.PICKED),
                        Order.OrderItem(pickedItem.hostId, Order.OrderItem.Status.PICKED),
                        Order.OrderItem(pickedItem.hostId, Order.OrderItem.Status.PICKED),
                        Order.OrderItem(pickedItem.hostId, Order.OrderItem.Status.PICKED),
                        Order.OrderItem(pickedItem.hostId, Order.OrderItem.Status.PICKED),
                        Order.OrderItem("testItem2", Order.OrderItem.Status.NOT_STARTED)
                    )
            )
        val pickedOrder = order.pick(listOf(pickedItem.hostId))
        val orderRepository = createInMemOrderRepo(mutableListOf(order))
        val cut =
            WLSService(
                createInMemItemRepo(mutableListOf(testItem)),
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        coEvery { catalogEventRepository.save(any()) } answers { ItemEvent(pickedItem) }
        coEvery { catalogEventProcessor.handleEvent(any()) } answers { }
        coEvery { storageEventProcessor.handleEvent(any()) } answers { }

        runTest {
            cut.pickOrderItems(HostName.AXIELL, listOf(testItem.hostId), order.hostOrderId)

            val orderFromRepo = orderRepository.getOrder(expectedOrder.hostName, expectedOrder.hostOrderId)
            assert(orderFromRepo != null)
            assert(orderFromRepo == expectedOrder)
            assert(pickedOrder == expectedOrder)
            coVerify(exactly = 1) { catalogEventRepository.save(any()) }
        }
    }

    @Test
    fun `pickOrderItems should set order to in progress during partial picking`() {
        val item1 = createTestItem()
        val item2 = createTestItem(hostId = "testItem-02")
        val itemsToPick = listOf(item1.hostId)
        val pickedItem = createTestItem(location = WITH_LENDER_LOCATION, quantity = 0)
        val order =
            createTestOrder(
                orderLine =
                    listOf(
                        Order.OrderItem(item1.hostId, Order.OrderItem.Status.NOT_STARTED),
                        Order.OrderItem(item2.hostId, Order.OrderItem.Status.NOT_STARTED)
                    )
            )
        val expectedOrder =
            order.copy(
                status = Order.Status.IN_PROGRESS,
                orderLine =
                    listOf(
                        Order.OrderItem(pickedItem.hostId, Order.OrderItem.Status.PICKED),
                        Order.OrderItem(item2.hostId, Order.OrderItem.Status.NOT_STARTED)
                    )
            )
        val pickedOrder = order.pick(itemsToPick)
        val orderRepository = createInMemOrderRepo(mutableListOf(order))
        val cut =
            WLSService(
                createInMemItemRepo(mutableListOf(item1, item2)),
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        coEvery { catalogEventRepository.save(any()) } answers { ItemEvent(pickedItem) }
        coEvery { catalogEventProcessor.handleEvent(any()) } answers { }
        coEvery { storageEventProcessor.handleEvent(any()) } answers { }

        runTest {
            cut.pickOrderItems(HostName.AXIELL, listOf(item1.hostId), order.hostOrderId)

            val orderFromRepo = orderRepository.getOrder(expectedOrder.hostName, expectedOrder.hostOrderId)
            assert(orderFromRepo != null)
            assert(orderFromRepo == expectedOrder)
            assert(pickedOrder == expectedOrder)
        }
    }

    @Test
    fun `getAllItems should return all items for given hosts`() {
        val i1 = createTestItem(hostName = HostName.AXIELL, hostId = "axiell-01")
        val i2 = createTestItem(hostName = HostName.AXIELL, hostId = "axiell-02")
        val i3 = createTestItem(hostName = HostName.ASTA, hostId = "asta-01")
        val i4 = createTestItem(hostName = HostName.ASTA, hostId = "asta-02")
        val i5 = createTestItem(hostName = HostName.ALMA, hostId = "alma-01")
        val itemRepo = createInMemItemRepo(mutableListOf(i1, i2, i3, i4, i5))
        val cut =
            WLSService(
                itemRepo,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        runTest {
            val axiell = cut.getAllItems(listOf(HostName.AXIELL))
            assertThat(axiell).hasSize(2)
            assertThat(axiell).containsExactlyInAnyOrder(i1, i2)

            val axiellAndAsta = cut.getAllItems(listOf(HostName.ASTA, HostName.AXIELL))
            assertThat(axiellAndAsta).hasSize(4)
            assertThat(axiellAndAsta).containsExactlyInAnyOrder(i1, i2, i3, i4)

            val axiellAstaAndAlma = cut.getAllItems(listOf(HostName.ASTA, HostName.AXIELL, HostName.ALMA))
            assertThat(axiellAstaAndAlma).hasSize(5)
            assertThat(axiellAstaAndAlma).containsExactlyInAnyOrder(i1, i2, i3, i4, i5)

            val unknown = cut.getAllItems(listOf(HostName.UNKNOWN))
            assertThat(unknown).isEmpty()
        }
    }

    @Test
    fun `getAllOrders should return all orders for given hosts`() {
        val i1 = createTestOrder(hostName = HostName.AXIELL, hostOrderId = "axiell-01")
        val i2 = createTestOrder(hostName = HostName.AXIELL, hostOrderId = "axiell-02")
        val i3 = createTestOrder(hostName = HostName.ASTA, hostOrderId = "asta-01")
        val i4 = createTestOrder(hostName = HostName.ASTA, hostOrderId = "asta-02")
        val i5 = createTestOrder(hostName = HostName.ALMA, hostOrderId = "alma-01")

        val orderRepo = createInMemOrderRepo(mutableListOf(i1, i2, i3, i4, i5))

        val cut =
            WLSService(
                itemRepository,
                orderRepo,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        runTest {
            val axiell = cut.getAllOrders(listOf(HostName.AXIELL))
            assertThat(axiell).hasSize(2)
            assertThat(axiell).containsExactlyInAnyOrder(i1, i2)

            val astaAndAxiell = cut.getAllOrders(listOf(HostName.ASTA, HostName.AXIELL))
            assertThat(astaAndAxiell).hasSize(4)
            assertThat(astaAndAxiell).containsExactlyInAnyOrder(i1, i2, i3, i4)

            val allOrders = cut.getAllOrders(listOf(HostName.ASTA, HostName.AXIELL, HostName.ALMA))
            assertThat(allOrders).hasSize(5)
            assertThat(allOrders).containsExactlyInAnyOrder(i1, i2, i3, i4, i5)

            val unknown = cut.getAllOrders(listOf(HostName.UNKNOWN))
            assertThat(unknown).isEmpty()
        }
    }

    @Test
    fun `synchronizeItems should update quantity and location, and add missing when synchronizing items`() {
        val testItem1 = createTestItem()
        val testItem2 = createTestItem(hostId = "missing-id-12345")
        val newQuantity = testItem.quantity + 1
        val newLocation = "SYNQ_WAREHOUSE"
        val itemRepository = createInMemItemRepo(mutableListOf(testItem1, testItem2))
        val cut =
            WLSService(
                itemRepository,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )
        val itemsToSync =
            listOf(
                SynchronizeItems.ItemToSynchronize(
                    hostName = testItem1.hostName,
                    hostId = testItem1.hostId,
                    quantity = newQuantity,
                    location = newLocation,
                    description = testItem1.description,
                    itemCategory = testItem1.itemCategory,
                    packaging = testItem1.packaging,
                    currentPreferredEnvironment = testItem1.preferredEnvironment,
                    associatedStorage = testItem1.associatedStorage
                ),
                // This item should be created
                SynchronizeItems.ItemToSynchronize(
                    hostName = HostName.AXIELL,
                    hostId = "some-unknown-id",
                    quantity = 1,
                    location = "SYNQ_WAREHOUSE",
                    description = "Some description",
                    itemCategory = ItemCategory.PAPER,
                    packaging = Packaging.BOX,
                    currentPreferredEnvironment = Environment.NONE,
                    associatedStorage = AssociatedStorage.SYNQ
                )
            )

        runTest {
            cut.synchronizeItems(itemsToSync)

            // Assert that quantity and location changed
            val updatedItem = itemRepository.getItem(testItem1.hostName, testItem1.hostId)
            assertThat(updatedItem?.quantity).isEqualTo(newQuantity)
            assertThat(updatedItem?.location).isEqualTo(newLocation)

            // Assert that other items are not changed
            val otherItem = itemRepository.getItem(testItem2.hostName, testItem2.hostId)
            assertThat(otherItem?.quantity).isEqualTo(testItem2.quantity)
            assertThat(otherItem?.location).isEqualTo(testItem2.location)

            // Assert that missing items are created
            val createdItem = itemRepository.getItem(HostName.AXIELL, "some-unknown-id")
            assertThat(createdItem).isNotNull
            assertThat(createdItem).matches {
                it?.quantity == 1 && it.location == "SYNQ_WAREHOUSE"
            }
        }
    }

    @Test
    fun `updateItem marks partially picked order items as returned`() {
        val storedItem = createTestItem(quantity = 0)
        val expectedItem = createTestItem(location = testUpdateItemPayload.location, quantity = testUpdateItemPayload.quantity)
        val testOrder =
            testOrder.copy(
                status = Order.Status.IN_PROGRESS,
                orderLine =
                    listOf(
                        Order.OrderItem(storedItem.hostId, Order.OrderItem.Status.PICKED),
                        Order.OrderItem("untouchedItem", Order.OrderItem.Status.NOT_STARTED)
                    )
            )
        val expectedOrder =
            testOrder.copy(
                status = Order.Status.IN_PROGRESS,
                orderLine =
                    listOf(
                        Order.OrderItem(expectedItem.hostId, Order.OrderItem.Status.RETURNED),
                        Order.OrderItem("untouchedItem", Order.OrderItem.Status.NOT_STARTED)
                    )
            )
        val cut =
            WLSService(
                createInMemItemRepo(mutableListOf(storedItem)),
                createInMemOrderRepo(mutableListOf(testOrder)),
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        coEvery { catalogEventRepository.save(any()) } returnsArgument (0)
        coEvery { catalogEventProcessor.handleEvent(any()) } answers {}

        runTest {
            cut.updateItem(testUpdateItemPayload)

            val item = cut.getItem(expectedItem.hostName, expectedItem.hostId)
            assert(item != null)
            assert(item == expectedItem)

            val order = cut.getOrder(expectedOrder.hostName, expectedOrder.hostOrderId)
            assert(order != null)
            assert(order == expectedOrder)
        }
    }

    @Test
    fun `updateItem should mark order items as returned`() {
        val storedItem = createTestItem(quantity = 0)
        val expectedItem = createTestItem(location = testUpdateItemPayload.location, quantity = testUpdateItemPayload.quantity)
        val testOrder =
            testOrder.copy(status = Order.Status.COMPLETED, orderLine = listOf(Order.OrderItem(storedItem.hostId, Order.OrderItem.Status.PICKED)))
        val expectedOrder =
            testOrder.copy(status = Order.Status.RETURNED, orderLine = listOf(Order.OrderItem(expectedItem.hostId, Order.OrderItem.Status.RETURNED)))
        val cut =
            WLSService(
                createInMemItemRepo(mutableListOf(storedItem)),
                createInMemOrderRepo(mutableListOf(testOrder)),
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        coEvery { catalogEventRepository.save(any()) } returnsArgument (0)
        coEvery { catalogEventProcessor.handleEvent(any()) } answers {}

        runTest {
            cut.updateItem(testUpdateItemPayload)

            val item = cut.getItem(expectedItem.hostName, expectedItem.hostId)
            assert(item != null)
            assert(item == expectedItem)

            val order = cut.getOrder(expectedOrder.hostName, expectedOrder.hostOrderId)
            assert(order != null)
            assert(order == expectedOrder)
        }
    }

    @Test
    fun `updateItem changes storage correctly when item is updated`() {
        val storedItem = createTestItem(quantity = 0, location = UNKNOWN_LOCATION)
        val expectedItem =
            createTestItem(
                location = testKardexUpdateItemPayload.location,
                quantity = testKardexUpdateItemPayload.quantity,
                associatedStorage = testKardexUpdateItemPayload.associatedStorage
            )
        val cut =
            WLSService(
                createInMemItemRepo(mutableListOf(storedItem)),
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        coEvery { catalogEventRepository.save(any()) } returnsArgument (0)
        coEvery { catalogEventProcessor.handleEvent(any()) } answers {}
        coEvery { orderRepository.getOrdersWithPickedItems(any(), any()) } returns listOf()

        runTest {
            cut.updateItem(testKardexUpdateItemPayload)

            val item = cut.getItem(expectedItem.hostName, expectedItem.hostId)
            assert(item != null)
            assert(item == expectedItem)
        }
    }

    @Test
    fun `moveItem changes storage correctly when item is updated`() {
        val storedItem = createTestItem(quantity = 0, location = UNKNOWN_LOCATION)
        val expectedItem =
            createTestItem(
                location = testKardexMoveItemPayload.location,
                quantity = testKardexMoveItemPayload.quantity,
                associatedStorage = testKardexMoveItemPayload.associatedStorage
            )
        val cut =
            WLSService(
                createInMemItemRepo(mutableListOf(storedItem)),
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        coEvery { catalogEventRepository.save(any()) } returnsArgument (0)
        coEvery { catalogEventProcessor.handleEvent(any()) } answers {}
        coEvery { orderRepository.getOrdersWithPickedItems(any(), any()) } returns listOf()

        runTest {
            cut.moveItem(testMoveItemPayload)

            val item = cut.getItem(expectedItem.hostName, expectedItem.hostId)
            assert(item != null)
            assert(item == expectedItem)
        }
    }

    @Test
    fun `moveItem should mark order items as returned`() {
        val storedItem = createTestItem(quantity = 0)
        val expectedItem = createTestItem(location = testMoveItemPayload.location, quantity = testMoveItemPayload.quantity)
        val testOrder =
            testOrder.copy(status = Order.Status.COMPLETED, orderLine = listOf(Order.OrderItem(storedItem.hostId, Order.OrderItem.Status.PICKED)))
        val expectedOrder =
            testOrder.copy(status = Order.Status.RETURNED, orderLine = listOf(Order.OrderItem(expectedItem.hostId, Order.OrderItem.Status.RETURNED)))

        val cut =
            WLSService(
                createInMemItemRepo(mutableListOf(storedItem)),
                createInMemOrderRepo(mutableListOf(testOrder)),
                catalogEventRepository,
                storageEventRepository,
                transactionPortExecutor,
                catalogEventProcessor,
                storageEventProcessor
            )

        coEvery { catalogEventRepository.save(any()) } returnsArgument (0)
        coEvery { catalogEventProcessor.handleEvent(any()) } answers {}

        runTest {
            cut.moveItem(testMoveItemPayload)

            val item = cut.getItem(expectedItem.hostName, expectedItem.hostId)
            assert(item != null)
            assert(item == expectedItem)

            val order = cut.getOrder(expectedOrder.hostName, expectedOrder.hostOrderId)
            assert(order != null)
            assert(order == expectedOrder)
        }
    }

    //
    // Test Helpers
    //

    private val testItem = createTestItem()

    private val testOrder = createTestOrder()
    private val testOrderItems = testOrder.orderLine.map { createTestItem(testOrder.hostName, it.hostId) }

    private val testMoveItemPayload =
        MoveItemPayload(
            hostName = testItem.hostName,
            hostId = testItem.hostId,
            quantity = 1,
            location = "KNOWN_LOCATION",
            associatedStorage = AssociatedStorage.SYNQ
        )

    private val testKardexMoveItemPayload =
        MoveItemPayload(
            hostName = testItem.hostName,
            hostId = testItem.hostId,
            quantity = 1,
            location = "KNOWN_LOCATION",
            associatedStorage = AssociatedStorage.KARDEX
        )

    private val testUpdateItemPayload =
        UpdateItemPayload(
            hostName = testItem.hostName,
            hostId = testItem.hostId,
            quantity = 1,
            location = "KNOWN_LOCATION",
            associatedStorage = AssociatedStorage.SYNQ
        )

    private val testKardexUpdateItemPayload =
        UpdateItemPayload(
            hostName = testItem.hostName,
            hostId = testItem.hostId,
            quantity = 1,
            location = "KNOWN_LOCATION",
            associatedStorage = AssociatedStorage.KARDEX
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

    private fun createInMemItemRepo(items: MutableList<Item>): ItemRepository {
        return object : ItemRepository {
            val itemList = items

            override suspend fun getItem(
                hostName: HostName,
                hostId: String
            ): Item? = items.firstOrNull { it.hostName == hostName && it.hostId == hostId }

            override suspend fun getItemsById(hostId: String): List<Item> = items.filter { it.hostId == hostId }

            override suspend fun getItemsByIds(
                hostName: HostName,
                hostIds: List<String>
            ): List<Item> =
                hostIds.mapNotNull { id ->
                    itemList.firstOrNull { it.hostName == hostName && it.hostId == id }
                }

            override suspend fun getAllItemsForHosts(hostnames: List<HostName>): List<Item> = items.filter { hostnames.contains(it.hostName) }

            override suspend fun createItem(item: Item): Item {
                val existingIndex = itemList.indexOfFirst { it.hostId == item.hostId && it.hostName == item.hostName }

                if (existingIndex == -1) {
                    itemList.add(item)
                } else {
                    itemList[existingIndex] = item
                }

                return item
            }

            override suspend fun doesEveryItemExist(ids: List<ItemRepository.ItemId>): Boolean =
                ids.all { id ->
                    itemList.any { it.hostId == id.hostId && it.hostName == id.hostName }
                }

            override suspend fun moveItem(
                hostName: HostName,
                hostId: String,
                quantity: Int,
                location: String,
                associatedStorage: AssociatedStorage
            ): Item = updateItem(hostId, hostName, quantity, location, associatedStorage)

            override suspend fun updateItem(
                hostId: String,
                hostName: HostName,
                quantity: Int,
                location: String,
                associatedStorage: AssociatedStorage
            ): Item {
                val item = itemList.first { it.hostName == hostName && it.hostId == hostId }
                val index = itemList.indexOf(item)
                val updatedItem =
                    createTestItem(
                        item.hostName,
                        item.hostId,
                        item.description,
                        item.itemCategory,
                        item.preferredEnvironment,
                        item.packaging,
                        item.callbackUrl,
                        location,
                        quantity,
                        associatedStorage
                    )
                itemList[index] = updatedItem

                return itemList[index]
            }
        }
    }

    private fun createInMemOrderRepo(orders: MutableList<Order>): OrderRepository {
        return object : OrderRepository {
            val orderList = orders

            override suspend fun getOrder(
                hostName: HostName,
                hostOrderId: String
            ): Order? = orderList.find { order -> order.hostName == hostName && order.hostOrderId == hostOrderId }

            override suspend fun getAllOrdersForHosts(hostnames: List<HostName>): List<Order> = orders.filter { hostnames.contains(it.hostName) }

            override suspend fun deleteOrder(order: Order) {
                orderList.removeIf { order1 -> order1.hostName == order.hostName && order1.hostOrderId == order.hostOrderId }
            }

            override suspend fun updateOrder(order: Order): Order {
                val originalOrder =
                    orderList.find { it.hostName == order.hostName && it.hostOrderId == order.hostOrderId }
                        ?: throw OrderNotFoundException("order not found")
                orderList.remove(originalOrder)
                if (orderList.add(order)) return order
                throw RuntimeException()
            }

            override suspend fun createOrder(order: Order): Order {
                orderList.add(order)
                return order
            }

            override suspend fun getOrdersWithItems(
                hostName: HostName,
                orderItemIds: List<String>
            ): List<Order> =
                orderList.filter { order ->
                    order.orderLine.any { orderItem -> orderItemIds.contains(orderItem.hostId) }
                }

            override suspend fun getOrdersWithPickedItems(
                hostName: HostName,
                orderItemIds: List<String>
            ): List<Order> =
                orderList
                    .filter { order ->
                        order.orderLine.any { orderItem ->
                            orderItemIds.contains(orderItem.hostId) &&
                                orderItem.status == Order.OrderItem.Status.PICKED
                        }
                    }

            override suspend fun getAllOrdersWithHostId(
                hostNames: List<HostName>,
                hostOrderId: String
            ): List<Order> =
                orderList
                    .filter { hostNames.contains(it.hostName) }
                    .filter { it.hostOrderId.lowercase() == hostOrderId.lowercase() }
        }
    }
}
