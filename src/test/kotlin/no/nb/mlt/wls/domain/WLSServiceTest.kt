package no.nb.mlt.wls.domain

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.WITH_LENDER_LOCATION
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem.UpdateItemPayload
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
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
    private val transactionPort = mockk<TransactionPort>()
    private val transactionPortSkipMock =
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
                transactionPort,
                catalogEventProcessor,
                storageEventProcessor
            )

        serviceAvecTrans =
            WLSService(
                itemRepository,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortSkipMock,
                catalogEventProcessor,
                storageEventProcessor
            )
    }

    @Test
    fun `addItem should save and return new item when it does not exists`() {
        val expectedItem = createTestItem()
        val itemCreatedEvent = ItemCreated(expectedItem)

        coEvery { itemRepository.getItem(expectedItem.hostName, expectedItem.hostId) } answers { null }
        coEvery { itemRepository.createItem(expectedItem) } answers { expectedItem }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } returns (expectedItem to itemCreatedEvent)
        coEvery { storageEventRepository.save(any()) } answers { itemCreatedEvent }
        coEvery { storageEventProcessor.handleEvent(itemCreatedEvent) } answers {}

        runTest {
            val addItemResult = serviceAvecTrans.addItem(expectedItem.toItemMetadata())

            assertThat(addItemResult).isEqualTo(expectedItem)
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
            createTestItem(hostName = startingItem.hostName, hostId = startingItem.hostId, location = WITH_LENDER_LOCATION, quantity = 0)
        val updateItemPayload =
            UpdateItemPayload(expectedItem.hostName, expectedItem.hostId, expectedItem.quantity, expectedItem.location)
        val itemUpdatedEvent = ItemEvent(expectedItem)

        coEvery { itemRepository.getItem(startingItem.hostName, startingItem.hostId) } answers { startingItem }
        coEvery {
            itemRepository.updateLocationAndQuantity(
                startingItem.hostId,
                startingItem.hostName,
                expectedItem.location,
                expectedItem.quantity
            )
        } answers { expectedItem }
        coEvery { catalogEventRepository.save(any()) } answers { itemUpdatedEvent }
        coEvery { catalogEventProcessor.handleEvent(itemUpdatedEvent) } answers {}

        runTest {
            val updateItemResult = serviceAvecTrans.updateItem(updateItemPayload)

            assertThat(updateItemResult).isEqualTo(expectedItem)
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(itemUpdatedEvent) }
        }
    }

    @Test
    fun `moveItem should change item's location and quantity`() {
        val startingItem = createTestItem(location = "SYNQ_WAREHOUSE", quantity = 1)
        val expectedItem =
            createTestItem(hostName = startingItem.hostName, hostId = startingItem.hostId, location = WITH_LENDER_LOCATION, quantity = -1)
        val moveItemPayload =
            MoveItemPayload(expectedItem.hostName, expectedItem.hostId, expectedItem.quantity, expectedItem.location)
        val itemMovedEvent = ItemEvent(expectedItem)

        coEvery { itemRepository.getItem(startingItem.hostName, startingItem.hostId) } answers { startingItem }
        coEvery {
            itemRepository.moveItem(
                startingItem.hostName,
                startingItem.hostId,
                startingItem.quantity + expectedItem.quantity,
                expectedItem.location
            )
        } answers { expectedItem }
        coEvery { catalogEventRepository.save(any()) } answers { itemMovedEvent }
        coEvery { catalogEventProcessor.handleEvent(itemMovedEvent) } answers {}

        runTest {
            val moveItemResult = serviceAvecTrans.moveItem(moveItemPayload)

            assertThat(moveItemResult).isEqualTo(expectedItem)
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
            coVerify(exactly = 0) { itemRepository.moveItem(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `pickItems should update items and send callbacks`() {
        val expectedItem = createTestItem(quantity = 0, location = WITH_LENDER_LOCATION)
        val pickedItemsMap = mapOf(expectedItem.hostId to 1)
        val itemPickedEvent = ItemEvent(expectedItem)
        coEvery { itemRepository.doesEveryItemExist(any()) } answers { true }
        coEvery { itemRepository.getItems(any(), any()) } answers { listOf(expectedItem) }
        coEvery { transactionPort.executeInTransaction<Any>(any()) } answers { itemPickedEvent }
        coEvery { catalogEventProcessor.handleEvent(itemPickedEvent) } answers {}

        runTest {
            serviceSansTrans.pickItems(expectedItem.hostName, pickedItemsMap)

            coVerify(exactly = 1) { itemRepository.doesEveryItemExist(any()) }
            coVerify(exactly = 1) { itemRepository.getItems(any(), any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(itemPickedEvent) }
        }
    }

    @Test
    fun `pickItems throw if item does not exist`() {
        val pickedItemsMap = mapOf(testItem.hostId to 1)
        coEvery { itemRepository.doesEveryItemExist(any()) } answers { false }

        runTest {
            assertThrows<ItemNotFoundException>(message = "Some items do not exist in the database, and were unable to be picked") {
                serviceSansTrans.pickItems(testItem.hostName, pickedItemsMap)
            }

            coVerify(exactly = 1) { itemRepository.doesEveryItemExist(any()) }
            coVerify(exactly = 0) { itemRepository.getItems(any(), any()) }
        }
    }

    @Test
    fun `pickOrderItems should update order and send callback`() {
        val expectedItem = createTestItem()
        val expectedOrder = testOrder.copy(orderLine = listOf(Order.OrderItem(expectedItem.hostId, Order.OrderItem.Status.PICKED)))
        val pickedItems = listOf(expectedItem.hostId)
        val orderItemsPickedEvent = OrderEvent(expectedOrder)
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { transactionPort.executeInTransaction<Any>(any()) } answers { orderItemsPickedEvent }
        coEvery { catalogEventProcessor.handleEvent(orderItemsPickedEvent) } answers {}

        runTest {
            serviceSansTrans.pickOrderItems(testOrder.hostName, pickedItems, testOrder.hostOrderId)

            coVerify(exactly = 1) { orderRepository.getOrder(any(), any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(orderItemsPickedEvent) }
        }
    }

    @Test
    fun `pickOrderItems should set order to in progress during partial picking`() {
        // Specific test setup
        val testItem1 = createTestItem()
        val unchangedTestItem = createTestItem(hostId = "test-item-2")
        val testOrder =
            createTestOrder(
                orderLine =
                    listOf(
                        Order.OrderItem(testItem1.hostId, Order.OrderItem.Status.NOT_STARTED),
                        Order.OrderItem(unchangedTestItem.hostId, Order.OrderItem.Status.NOT_STARTED)
                    )
            )
        val expectedItem1 = testItem1.pickItem(1)
        val expectedOrder =
            testOrder.copy(
                status = Order.Status.IN_PROGRESS,
                orderLine =
                    listOf(
                        Order.OrderItem(expectedItem1.hostId, Order.OrderItem.Status.PICKED),
                        Order.OrderItem(unchangedTestItem.hostId, Order.OrderItem.Status.NOT_STARTED)
                    )
            )
        val itemsToPick = listOf(testItem1.hostId)
        coEvery { catalogEventRepository.save(any()) } answers { ItemEvent(expectedItem1) }
        coEvery { catalogEventProcessor.handleEvent(any()) } answers { }
        coEvery { storageEventProcessor.handleEvent(any()) } answers { }

        val itemRepoMock = createInMemItemRepo(mutableListOf(testItem1, unchangedTestItem))
        val orderRepoMock = createInMemOrderRepo(mutableListOf(testOrder))

        val cut =
            WLSService(
                itemRepoMock,
                orderRepoMock,
                catalogEventRepository,
                storageEventRepository,
                transactionPortSkipMock,
                catalogEventProcessor,
                storageEventProcessor
            )

        runTest {
            val updatedOrder = testOrder.pickItems(itemsToPick)
            cut.pickOrderItems(HostName.AXIELL, listOf(testItem1.hostId), testOrder.hostOrderId)

            val order = orderRepoMock.getOrder(expectedOrder.hostName, expectedOrder.hostOrderId)
            assert(order != null)
            assert(order == expectedOrder)
            assert(updatedOrder == expectedOrder)
        }
    }

    @Test
    fun `createOrder should save order in db and outbox`() {
        val order = createTestOrder()
        val orderCreatedEvent = OrderCreated(order)

        coEvery { orderRepository.getOrder(createOrderDTO.hostName, createOrderDTO.hostOrderId) } answers { null }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } answers { testOrder to orderCreatedEvent }
        coEvery { storageEventProcessor.handleEvent(orderCreatedEvent) } answers {}

        val itemRepoMock =
            createInMemItemRepo(
                order.orderLine
                    .map {
                        createTestItem(
                            hostId = it.hostId,
                            hostName = order.hostName
                        )
                    }.toMutableList()
            )

        val cut =
            WLSService(
                itemRepoMock,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPort,
                catalogEventProcessor,
                storageEventProcessor
            )

        runTest {
            val createOrderResult = cut.createOrder(createOrderDTO)
            assertThat(createOrderResult).isEqualTo(testOrder)
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(orderCreatedEvent) }
        }
    }

    @Test
    fun `createOrder should return existing order when trying to create one with same id and host`() {
        val duplicateOrder = createOrderDTO.copy(callbackUrl = "https://new-callback-wls.no/order")
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }

        runTest {
            val createOrderResult = serviceSansTrans.createOrder(duplicateOrder)

            assertThat(createOrderResult).isEqualTo(testOrder)
            assertThat(createOrderResult.callbackUrl).isEqualTo(testOrder.callbackUrl)
            coVerify(exactly = 0) { itemRepository.doesEveryItemExist(any()) }
        }
    }

    @Test
    fun `createOrder should create items with unknown properties if they do not exist`() {
        val existingItems = mutableListOf(createTestItem(hostName = HostName.AXIELL, hostId = "1"))
        val unknownItems = mutableListOf(createTestItem(hostName = HostName.AXIELL, hostId = "2"))
        val testOrder =
            createTestOrder(
                hostOrderId = "testOrder",
                orderLine =
                    (existingItems + unknownItems).map {
                        Order.OrderItem(it.hostId, Order.OrderItem.Status.NOT_STARTED)
                    }
            )

        val itemRepoMock = createInMemItemRepo(existingItems)
        coEvery { orderRepository.getOrder(HostName.AXIELL, "testOrder") } answers { null }
        coEvery { orderRepository.createOrder(testOrder) } answers { testOrder }
        coEvery { catalogEventProcessor.handleEvent(any()) } answers { }
        coEvery { storageEventRepository.save(any()) } answers { OrderCreated(testOrder) }
        coEvery { storageEventProcessor.handleEvent(any()) } answers { }

        val cut =
            WLSService(
                itemRepoMock,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortSkipMock,
                catalogEventProcessor,
                storageEventProcessor
            )

        runTest {
            cut.createOrder(
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
            )

            assert(itemRepoMock.doesEveryItemExist(testOrder.orderLine.map { ItemRepository.ItemId(testOrder.hostName, it.hostId) }))
            assertThat(itemRepoMock.getItem(HostName.AXIELL, "2")).isNotNull
        }
    }

    @Test
    fun `deleteOrder should complete when order exists`() {
        val deletedOrderEvent = OrderDeleted(testOrder.hostName, testOrder.hostOrderId)
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { transactionPort.executeInTransaction<Any>(any()) } answers { deletedOrderEvent }
        coEvery { storageEventProcessor.handleEvent(deletedOrderEvent) } answers {}

        runTest {
            serviceSansTrans.deleteOrder(testOrder.hostName, testOrder.hostOrderId)
            coVerify(exactly = 1) { orderRepository.getOrder(any(), any()) }
            coVerify { storageEventProcessor.handleEvent(deletedOrderEvent) }
        }
    }

    @Test
    fun `deleteOrder should fail when order does not exist in WLS DB`() {
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }

        runTest {
            assertThrows<OrderNotFoundException>(
                message = "No order with hostOrderId: $testOrder.hostOrderId and hostName: $testOrder.hostName exists"
            ) {
                serviceSansTrans.deleteOrder(testOrder.hostName, testOrder.hostOrderId)
            }
            coVerify(exactly = 1) { orderRepository.getOrder(any(), any()) }
            coVerify(exactly = 0) { transactionPort.executeInTransaction<Any>(any()) }
        }
    }

    @Test
    fun `deleteOrder should fail when order deletion fails`() {
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { storageSystemRepoMock.deleteOrder(any(), any()) } throws StorageSystemException("Order not found", null)

        runTest {
            assertThrows<RuntimeException>(message = "Could not delete order") {
                serviceSansTrans.deleteOrder(testOrder.hostName, testOrder.hostOrderId)
            }
            coVerify(exactly = 1) { orderRepository.getOrder(any(), any()) }
            coVerify(exactly = 1) { transactionPort.executeInTransaction<Any>(any()) }
        }
    }

    @Test
    fun `updateOrderStatus should update status and send callback`() {
        val completedOrder = testOrder.copy(status = Order.Status.COMPLETED)
        val orderStatusUpdatedEvent = OrderEvent(completedOrder)
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } answers { completedOrder to orderStatusUpdatedEvent }
        coEvery { catalogEventProcessor.handleEvent(orderStatusUpdatedEvent) } answers { }

        runTest {
            val updateOrderStatusResult = serviceSansTrans.updateOrderStatus(testOrder.hostName, testOrder.hostOrderId, Order.Status.COMPLETED)

            assertThat(updateOrderStatusResult).isEqualTo(completedOrder)
            coVerify(exactly = 1) { orderRepository.getOrder(any(), any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(orderStatusUpdatedEvent) }
        }
    }

    @Test
    fun `updateOrderStatus should fail if order does not exist`() {
        coEvery { orderRepository.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }

        runTest {
            assertThrows<OrderNotFoundException>(
                message = "No order with hostName: ${testOrder.hostName} and hostOrderId: ${testOrder.hostOrderId} exists"
            ) {
                serviceSansTrans.updateOrderStatus(testOrder.hostName, testOrder.hostOrderId, Order.Status.COMPLETED)
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
                transactionPortSkipMock,
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

            val none = cut.getAllItems(listOf(HostName.NONE))
            assertThat(none).isEmpty()
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
                transactionPortSkipMock,
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

            val none = cut.getAllOrders(listOf(HostName.NONE))
            assertThat(none).isEmpty()
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
    fun `should update quantity and location, and add missing when synchronizing items`() {
        val testItem1 = createTestItem()
        val testItem2 = createTestItem(hostId = "missing-id-12345")
        val itemRepo = createInMemItemRepo(mutableListOf(testItem1, testItem2))

        val service =
            WLSService(
                itemRepo,
                orderRepository,
                catalogEventRepository,
                storageEventRepository,
                transactionPortSkipMock,
                catalogEventProcessor,
                storageEventProcessor
            )

        runTest {
            val newQuantity = testItem.quantity + 1
            val newLocation = "SYNQ_WAREHOUSE"

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
                        currentPreferredEnvironment = testItem1.preferredEnvironment
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
                        currentPreferredEnvironment = Environment.NONE
                    )
                )

            service.synchronizeItems(itemsToSync)

            // Assert that quantity and location changed
            val updatedItem = itemRepo.getItem(testItem1.hostName, testItem1.hostId)
            assertThat(updatedItem?.quantity).isEqualTo(newQuantity)
            assertThat(updatedItem?.location).isEqualTo(newLocation)

            // Assert that other items are not changed
            val otherItem = itemRepo.getItem(testItem2.hostName, testItem2.hostId)
            assertThat(otherItem?.quantity).isEqualTo(testItem2.quantity)
            assertThat(otherItem?.location).isEqualTo(testItem2.location)

            // Assert that missing items are created
            val createdItem = itemRepo.getItem(HostName.AXIELL, "some-unknown-id")
            assertThat(createdItem).isNotNull
            assertThat(createdItem).matches {
                it?.quantity == 1 && it.location == "SYNQ_WAREHOUSE"
            }
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
        val inMemItemRepo = createInMemItemRepo(mutableListOf(storedItem))
        val inMemOrderRepo = createInMemOrderRepo(mutableListOf(testOrder))

        coEvery { catalogEventRepository.save(any()) } returnsArgument (0)
        coEvery { catalogEventProcessor.handleEvent(any()) } answers {}

        serviceSansTrans =
            WLSService(
                inMemItemRepo,
                inMemOrderRepo,
                catalogEventRepository,
                storageEventRepository,
                transactionPortSkipMock,
                catalogEventProcessor,
                storageEventProcessor
            )

        runTest {
            serviceSansTrans.updateItem(testUpdateItemPayload)
            val item = serviceSansTrans.getItem(expectedItem.hostName, expectedItem.hostId)
            assert(item != null)
            assert(item == expectedItem)
            val order = inMemOrderRepo.getOrder(expectedOrder.hostName, expectedOrder.hostOrderId)
            assert(order != null)
            assert(order == expectedOrder)
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
        val inMemItemRepo = createInMemItemRepo(mutableListOf(storedItem))
        val inMemOrderRepo = createInMemOrderRepo(mutableListOf(testOrder))

        coEvery { catalogEventRepository.save(any()) } returnsArgument (0)
        coEvery { catalogEventProcessor.handleEvent(any()) } answers {}

        serviceSansTrans =
            WLSService(
                inMemItemRepo,
                inMemOrderRepo,
                catalogEventRepository,
                storageEventRepository,
                transactionPortSkipMock,
                catalogEventProcessor,
                storageEventProcessor
            )

        runTest {
            serviceSansTrans.moveItem(testMoveItemPayload)
            val item = serviceSansTrans.getItem(expectedItem.hostName, expectedItem.hostId)
            assert(item != null)
            assert(item == expectedItem)
            val order = inMemOrderRepo.getOrder(expectedOrder.hostName, expectedOrder.hostOrderId)
            assert(order != null)
            assert(order == expectedOrder)
        }
    }

    //
    // Test Helpers
    //

    private val testItem = createTestItem()

    private val testOrder = createTestOrder()

    private val testMoveItemPayload =
        MoveItemPayload(
            hostName = testItem.hostName,
            hostId = testItem.hostId,
            quantity = 1,
            location = "KNOWN_LOCATION"
        )

    private val testUpdateItemPayload =
        UpdateItem.UpdateItemPayload(
            hostName = testItem.hostName,
            hostId = testItem.hostId,
            quantity = 1,
            location = "KNOWN_LOCATION"
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

            override suspend fun getItems(
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
                location: String
            ): Item = updateLocationAndQuantity(hostId, hostName, location, quantity)

            override suspend fun updateLocationAndQuantity(
                hostId: String,
                hostName: HostName,
                location: String,
                quantity: Int
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
                        quantity
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
            ): List<Order> {
                val o =
                    orderList.filter { order ->
                        order.orderLine.any { orderItem -> orderItemIds.contains(orderItem.hostId) }
                    }
                return o
            }
        }
    }
}
