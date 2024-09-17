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
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
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

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock)
        runTest {
            val order = cut.addItem(
                ItemMetadata(
                    hostId = testItem.hostId,
                    hostName = testItem.hostName,
                    description = testItem.description,
                    productCategory = testItem.productCategory,
                    preferredEnvironment = testItem.preferredEnvironment,
                    packaging = testItem.packaging,
                    owner = testItem.owner,
                )
            )

            assertThat(order).isEqualTo(expectedItem)
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

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock)

        runTest {
            val item = cut.addItem(
                ItemMetadata(
                    hostId = testItem.hostId,
                    hostName = testItem.hostName,
                    description = testItem.description,
                    productCategory = testItem.productCategory,
                    preferredEnvironment = testItem.preferredEnvironment,
                    packaging = testItem.packaging,
                    owner = testItem.owner,
                )
            )

            assertThat(item).isEqualTo(testItem)

            coVerify(exactly = 0) { itemRepoMock.createItem(any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.createItem(any()) }
        }

    }

    @Test
    fun `getItem should return requested item when it exists in DB`() {
        val expectedItem = testItem.copy()

        coEvery { itemRepoMock.getItem(HostName.AXIELL, "12345") } answers { expectedItem }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock)
        runTest {
            val order = cut.getItem(HostName.AXIELL, "12345")
            assertThat(order).isEqualTo(expectedItem)
        }
    }

    @Test
    fun `getItem should return null if item does not exist`() {
        coEvery { itemRepoMock.getItem(HostName.AXIELL, "12345") } answers { null }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock)
        runTest {
            val order = cut.getItem(HostName.AXIELL, "12345")
            assertThat(order).isEqualTo(null)
        }
    }

    @Test
    fun `createOrder should save order in db and storage system`() {
        val expectedOrder = testOrder.copy()

        coEvery { orderRepoMock.getOrder(any(), any()) } answers { null }
        coEvery { itemRepoMock.doesAllItemsExist(any()) } answers { true }
        coEvery { orderRepoMock.createOrder(any()) } answers { expectedOrder }
        coJustRun { storageSystemRepoMock.createOrder(any()) }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock)
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

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock)
        runTest {
            val order = cut.createOrder(
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
        coEvery { itemRepoMock.doesAllItemsExist(any()) } answers { false }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock)
        runTest {
            assertThrows<ValidationException> {
                cut.createOrder(testOrder.toCreateOrderDTO())
            }
            coVerify(exactly = 0) { orderRepoMock.createOrder(any()) }
            coVerify(exactly = 0) { storageSystemRepoMock.createOrder(any()) }
        }
    }

    // Test delete order when order exists

    // Test delete order when order does not exist

    // Test update order when order exists

    // Test update order when order does not exist

    // Test update order when order items do not exist

    @Test
    fun `getOrder should return requested order when it exists in DB`() {
        val expectedItem = testOrder.copy()

        coEvery { orderRepoMock.getOrder(HostName.AXIELL, "12345") } answers { expectedItem }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock)
        runTest {
            val order = cut.getOrder(HostName.AXIELL, "12345")
            assertThat(order).isEqualTo(expectedItem)
        }
    }

    @Test
    fun `getOrder should return null when order does not exists in DB`() {
        coEvery { orderRepoMock.getOrder(any(), any()) } answers { null }

        val cut = WLSService(itemRepoMock, orderRepoMock, storageSystemRepoMock)
        runTest {
            val order = cut.getOrder(HostName.AXIELL, "12345")
            assertThat(order).isNull()
        }
    }

    private val testItem = Item(
        hostName = HostName.AXIELL,
        hostId = "12345",
        description = "Tyven, tyven skal du hete",
        productCategory = "BOOK",
        preferredEnvironment = Environment.NONE,
        packaging = Packaging.NONE,
        owner = Owner.NB,
        location = null,
        quantity = null
    )

    private val testOrder = Order(
        hostName = HostName.AXIELL,
        hostOrderId = "12345",
        status = Order.Status.NOT_STARTED,
        productLine = listOf(),
        orderType = Order.Type.LOAN,
        owner = Owner.NB,
        receiver = Order.Receiver(
            name = "Kåre",
            address = "Kåres gate 1",
            postalCode = "1234",
            city = "Kåresby",
            location = "Kåresplass",
            phoneNumber = "99999999"
        ),
        callbackUrl = "http://callback.url/path"
    )

    private fun Order.toCreateOrderDTO() = CreateOrderDTO(
        hostName = testOrder.hostName,
        hostOrderId = testOrder.hostOrderId,
        orderItems = testOrder.productLine.map { CreateOrderDTO.OrderItem(it.hostId) },
        orderType = testOrder.orderType,
        owner = testOrder.owner,
        receiver = testOrder.receiver,
        callbackUrl = testOrder.callbackUrl
    )

}
