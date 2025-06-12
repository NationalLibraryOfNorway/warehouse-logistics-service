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
import no.nb.mlt.wls.domain.model.events.storage.OrderUpdated
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
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
    private val itemRepoMock = mockk<ItemRepository>()
    private val orderRepoMock = mockk<OrderRepository>()
    private val catalogEventRepository = mockk<EventRepository<CatalogEvent>>()
    private val storageEventRepository = mockk<EventRepository<StorageEvent>>()
    private val transactionPort = mockk<TransactionPort>()
    private val catalogEventProcessor = mockk<EventProcessor<CatalogEvent>>()
    private val storageEventProcessor = mockk<EventProcessor<StorageEvent>>()
    private val storageSystemRepoMock = mockk<StorageSystemFacade>()
    private val transactionPortSkipMock =
        object : TransactionPort {
            override suspend fun <T> executeInTransaction(action: suspend () -> T): T = action()
        }

    private lateinit var cut: WLSService

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()

        cut =
            WLSService(
                itemRepoMock,
                orderRepoMock,
                catalogEventRepository,
                storageEventRepository,
                transactionPort,
                catalogEventProcessor,
                storageEventProcessor
            )
    }

    @Test
    fun `addItem should save and return new item when it does not exists`() {
        val expectedItem = createTestItem()
        val itemCreatedEvent = ItemCreated(expectedItem)
        coEvery { itemRepoMock.getItem(expectedItem.hostName, expectedItem.hostId) } answers { null }
        coEvery { itemRepoMock.createItem(expectedItem) } answers { expectedItem }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } returns (expectedItem to itemCreatedEvent)
        coEvery { storageEventProcessor.handleEvent(itemCreatedEvent) } answers {}

        runTest {
            val addItemResult = cut.addItem(expectedItem.toItemMetadata())

            assertThat(addItemResult).isEqualTo(expectedItem)
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(itemCreatedEvent) }
        }
    }

    @Test
    fun `addItem should not save new item but return existing item if it already exists`() {
        val expectedItem = createTestItem()
        coEvery { itemRepoMock.getItem(expectedItem.hostName, expectedItem.hostId) } answers { expectedItem }
        coEvery { itemRepoMock.createItem(expectedItem) } answers { expectedItem }
        coJustRun { storageSystemRepoMock.createItem(any()) }

        runTest {
            val addItemResult = cut.addItem(expectedItem.toItemMetadata())

            assertThat(addItemResult).isEqualTo(expectedItem)
            coVerify(exactly = 0) { itemRepoMock.createItem(any()) }
            coVerify(exactly = 0) { storageEventProcessor.handleEvent(any()) }
        }
    }

    @Test
    fun `moveItem should return when item successfully moves`() {
        val expectedItem = createTestItem(location = testMoveItemPayload.location, quantity = testMoveItemPayload.quantity)
        val itemMovedEvent = ItemEvent(expectedItem)
        coEvery { itemRepoMock.getItem(expectedItem.hostName, expectedItem.hostId) } answers { expectedItem }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } returns (expectedItem to itemMovedEvent)
        coEvery { catalogEventProcessor.handleEvent(itemMovedEvent) } answers {}

        runTest {
            val moveItemResult = cut.moveItem(testMoveItemPayload)
            assertThat(moveItemResult).isEqualTo(expectedItem)

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
    fun `pickItems should update items and send callbacks`() {
        val expectedItem = createTestItem(quantity = 0, location = WITH_LENDER_LOCATION)
        val pickedItemsMap = mapOf(expectedItem.hostId to 1)
        val itemPickedEvent = ItemEvent(expectedItem)
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { itemRepoMock.getItems(any(), any()) } answers { listOf(expectedItem) }
        coEvery { transactionPort.executeInTransaction<Any>(any()) } answers { itemPickedEvent }
        coEvery { catalogEventProcessor.handleEvent(itemPickedEvent) } answers {}

        runTest {
            cut.pickItems(expectedItem.hostName, pickedItemsMap)

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
        val expectedItem = createTestItem()
        val expectedOrder = testOrder.copy(orderLine = listOf(Order.OrderItem(expectedItem.hostId, Order.OrderItem.Status.PICKED)))
        val pickedItems = listOf(expectedItem.hostId)
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
        val order = createTestOrder()
        val orderCreatedEvent = OrderCreated(order)

        coEvery { orderRepoMock.getOrder(createOrderDTO.hostName, createOrderDTO.hostOrderId) } answers { null }
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
                orderRepoMock,
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
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }

        runTest {
            val createOrderResult = cut.createOrder(duplicateOrder)

            assertThat(createOrderResult).isEqualTo(testOrder)
            assertThat(createOrderResult.callbackUrl).isEqualTo(testOrder.callbackUrl)
            coVerify(exactly = 0) { itemRepoMock.doesEveryItemExist(any()) }
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
        coEvery { orderRepoMock.getOrder(HostName.AXIELL, "testOrder") } answers { null }
        coEvery { orderRepoMock.createOrder(testOrder) } answers { testOrder }
        coEvery { catalogEventProcessor.handleEvent(any()) } answers { }
        coEvery { storageEventRepository.save(any()) } answers { OrderCreated(testOrder) }
        coEvery { storageEventProcessor.handleEvent(any()) } answers { }

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
            assertThrows<OrderNotFoundException>(
                message = "No order with hostOrderId: $testOrder.hostOrderId and hostName: $testOrder.hostName exists"
            ) {
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
        val updateOrderEvent = OrderUpdated(updatedOrder)
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { orderRepoMock.getOrder(updatedOrder.hostName, updatedOrder.hostOrderId) } answers { testOrder }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } answers { updatedOrder to updateOrderEvent }
        coEvery { storageEventProcessor.handleEvent(updateOrderEvent) } answers { }

        runTest {
            val updateOrderResult = callUpdateOrder()

            assertThat(updateOrderResult).isEqualTo(updatedOrder)
            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 1) { storageEventProcessor.handleEvent(updateOrderEvent) }
        }
    }

    @Test
    fun `updateOrder should fail if items don't exist`() {
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { false }

        runTest {
            assertThrows<ValidationException>(message = "All order items in order must exist") {
                callUpdateOrder()
            }

            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 0) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 0) { storageEventProcessor.handleEvent(any()) }
        }
    }

    @Test
    fun `updateOrder should fail when order does not exist`() {
        coEvery { itemRepoMock.doesEveryItemExist(any()) } answers { true }
        coEvery { orderRepoMock.getOrder(any(), any()) } throws OrderNotFoundException("Order not found")

        runTest {
            assertThrows<OrderNotFoundException>(message = "Order not found") {
                callUpdateOrder()
            }

            coVerify(exactly = 1) { itemRepoMock.doesEveryItemExist(any()) }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 0) { storageEventProcessor.handleEvent(any()) }
        }
    }

    @Test
    fun `updateOrderStatus should update status and send callback`() {
        val completedOrder = testOrder.copy(status = Order.Status.COMPLETED)
        val orderStatusUpdatedEvent = OrderEvent(completedOrder)
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { testOrder }
        coEvery { transactionPort.executeInTransaction<Pair<Any, Any>>(any()) } answers { completedOrder to orderStatusUpdatedEvent }
        coEvery { catalogEventProcessor.handleEvent(orderStatusUpdatedEvent) } answers { }

        runTest {
            val updateOrderStatusResult = cut.updateOrderStatus(testOrder.hostName, testOrder.hostOrderId, Order.Status.COMPLETED)

            assertThat(updateOrderStatusResult).isEqualTo(completedOrder)
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 1) { catalogEventProcessor.handleEvent(orderStatusUpdatedEvent) }
        }
    }

    @Test
    fun `updateOrderStatus should fail if order does not exist`() {
        coEvery { orderRepoMock.getOrder(testOrder.hostName, testOrder.hostOrderId) } answers { null }

        runTest {
            assertThrows<OrderNotFoundException>(
                message = "No order with hostName: ${testOrder.hostName} and hostOrderId: ${testOrder.hostOrderId} exists"
            ) {
                cut.updateOrderStatus(testOrder.hostName, testOrder.hostOrderId, Order.Status.COMPLETED)
            }
            coVerify(exactly = 1) { orderRepoMock.getOrder(any(), any()) }
            coVerify(exactly = 0) { catalogEventProcessor.handleEvent(any()) }
        }
    }

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

    @Test
    fun `should update quantity and location, and add missing when synchronizing items`() {
        val testItem1 = createTestItem()
        val testItem2 = createTestItem(hostId = "missing-id-12345")
        val itemRepo = createInMemItemRepo(mutableListOf(testItem1, testItem2))

        val service =
            WLSService(
                itemRepo,
                orderRepoMock,
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
    fun `moveItem should return items for orders`() {
        val storedItem = createTestItem(quantity = 0)
        val expectedItem = createTestItem(location = testMoveItemPayload.location, quantity = testMoveItemPayload.quantity)
        val testOrder =
            testOrder.copy(status = Order.Status.COMPLETED, orderLine = listOf(Order.OrderItem(storedItem.hostId, Order.OrderItem.Status.PICKED)))
        val expectedOrder =
            testOrder.copy(status = Order.Status.RETURNED, orderLine = listOf(Order.OrderItem(expectedItem.hostId, Order.OrderItem.Status.RETURNED)))
        val inMemItemRepo = createInMemItemRepo(mutableListOf(storedItem))
        val inMemOrderRepo = createInMemoOrderRepo(mutableListOf(testOrder))

        coEvery { catalogEventRepository.save(any()) } returnsArgument (0)
        coEvery { catalogEventProcessor.handleEvent(any()) } answers {}

        cut =
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
            cut.moveItem(testMoveItemPayload)
            val item = cut.getItem(expectedItem.hostName, expectedItem.hostId)
            assert(item != null)
            assert(item == expectedItem)
            val order = inMemOrderRepo.getOrder(expectedOrder.hostName, expectedOrder.hostOrderId)
            assert(order != null)
            assert(order == expectedOrder)
        }
    }

    @Test
    fun `updateItem should return items for orders`() {
        val storedItem = createTestItem(quantity = 0)
        val expectedItem = createTestItem(location = testUpdateItemPayload.location, quantity = testUpdateItemPayload.quantity)
        val testOrder =
            testOrder.copy(status = Order.Status.COMPLETED, orderLine = listOf(Order.OrderItem(storedItem.hostId, Order.OrderItem.Status.PICKED)))
        val expectedOrder =
            testOrder.copy(status = Order.Status.RETURNED, orderLine = listOf(Order.OrderItem(expectedItem.hostId, Order.OrderItem.Status.RETURNED)))
        val inMemItemRepo = createInMemItemRepo(mutableListOf(storedItem))
        val inMemOrderRepo = createInMemoOrderRepo(mutableListOf(testOrder))

        coEvery { catalogEventRepository.save(any()) } returnsArgument (0)
        coEvery { catalogEventProcessor.handleEvent(any()) } answers {}

        cut =
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
            cut.updateItem(testUpdateItemPayload)
            val item = cut.getItem(expectedItem.hostName, expectedItem.hostId)
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

    private val updatedOrder = createTestOrder()

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

    private suspend fun callUpdateOrder() =
        cut.updateOrder(
            updatedOrder.hostName,
            updatedOrder.hostOrderId,
            updatedOrder.orderLine.map { it.hostId },
            updatedOrder.orderType,
            updatedOrder.contactPerson,
            updatedOrder.address,
            updatedOrder.note,
            updatedOrder.callbackUrl
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

    private fun createInMemoOrderRepo(orders: MutableList<Order>): OrderRepository {
        return object : OrderRepository {
            val orderList = orders

            override suspend fun getOrder(
                hostName: HostName,
                hostOrderId: String
            ): Order? = orderList.find { order -> order.hostName == hostName && order.hostOrderId == hostOrderId }

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

            override suspend fun getOrdersWithItem(
                hostName: HostName,
                returnedItems: List<String>
            ): List<Order> {
                val o =
                    orderList.filter { order ->
                        order.orderLine.any { orderItem -> returnedItems.contains(orderItem.hostId) }
                    }
                return o
            }
        }
    }
}
