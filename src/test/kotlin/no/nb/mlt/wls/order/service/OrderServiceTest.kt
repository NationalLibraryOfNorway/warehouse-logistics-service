package no.nb.mlt.wls.order.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.order.model.OrderLineStatus
import no.nb.mlt.wls.order.model.OrderReceiver
import no.nb.mlt.wls.order.model.OrderStatus
import no.nb.mlt.wls.order.model.OrderType
import no.nb.mlt.wls.order.model.ProductLine
import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.payloads.ApiUpdateOrderPayload
import no.nb.mlt.wls.order.payloads.toOrder
import no.nb.mlt.wls.order.repository.OrderRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerErrorException
import org.springframework.web.server.ServerWebInputException
import reactor.core.publisher.Mono

@Suppress("ReactiveStreamsUnusedPublisher")
@TestInstance(PER_CLASS)
@ExtendWith(MockKExtension::class)
class OrderServiceTest {
    @MockK
    private lateinit var db: OrderRepository

    @MockK
    private lateinit var synq: SynqOrderService

    @InjectMockKs
    private lateinit var cut: OrderService // cut = class under test

    @Test
    fun `save called with payload missing orderId throws`() {
        assertExceptionThrownWithMessage(op.copy(orderId = ""), "orderId is required", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(op.copy(orderId = "\t\n"), "orderId is required", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(op.copy(orderId = "      "), "orderId is required", ServerWebInputException::class.java)
    }

    @Test
    fun `save called with payload missing hostOrderId throws`() {
        assertExceptionThrownWithMessage(op.copy(hostOrderId = ""), "hostOrderId is required", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(op.copy(hostOrderId = "\t\n"), "hostOrderId is required", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(op.copy(hostOrderId = "      "), "hostOrderId is required", ServerWebInputException::class.java)
    }

    @Test
    fun `save with payload missing product lines throws`() {
        assertExceptionThrownWithMessage(op.copy(productLine = listOf()), "must contain product lines", ServerWebInputException::class.java)
    }

    @Test
    fun `save when order exists throws`() {
        runTest {
            every { db.findByHostNameAndHostOrderId(op.hostName, op.hostOrderId) } returns Mono.just(op.toOrder())
            assertThat(cut.createOrder(op, client).statusCode.is4xxClientError)
        }
    }

    @Test
    fun `save called with Order that SynQ says exists throws`() {
        runTest {
            every { db.findByHostNameAndHostOrderId(op.hostName, op.hostOrderId) } returns Mono.empty()
            coEvery { synq.createOrder(any()) } throws ServerErrorException("Duplicate order found in in SynQ", null)

            assertExceptionThrownWithMessage(op, "Duplicate order", ServerErrorException::class.java)
        }
    }

    @Test
    fun `save called when SynQ fails is handled gracefully`() {
        every { db.findByHostNameAndHostOrderId(op.hostName, op.hostOrderId) } returns Mono.empty()
        coEvery { synq.createOrder(any()) } throws ServerErrorException("Unexpected error", null)

        assertThatExceptionOfType(ServerErrorException::class.java).isThrownBy {
            runBlocking {
                cut.createOrder(op, client)
            }
        }
    }

    @Test
    fun `save called when db is down is handled gracefully`() {
        runTest {
            every { db.findByHostNameAndHostOrderId(any(), any()) } returns Mono.error(Exception("db is down"))

            assertThatExceptionOfType(ServerErrorException::class.java).isThrownBy {
                runBlocking {
                    cut.createOrder(op, client)
                }
            }
        }
    }

    @Test
    fun `save with no errors returns created order`() {
        runTest {
            every { db.findByHostNameAndHostOrderId(op.hostName, op.hostOrderId) } returns Mono.empty()
            coEvery { synq.createOrder(any()) } returns ResponseEntity(HttpStatus.CREATED)
            every { db.save(any()) } returns Mono.just(op.toOrder())

            assertThat(cut.createOrder(op, client).statusCode.is2xxSuccessful)
        }
    }

    @Test
    fun `update existing order with no errors returns ok`() {
        runTest {
            every { db.findByHostNameAndHostOrderId(updateOrderPayload.hostName, updateOrderPayload.hostOrderId) } returns Mono.just(op.toOrder())
            coEvery { synq.updateOrder(any()) } returns ResponseEntity.ok().build()
            every { db.save(any()) } returns Mono.just(op.toOrder())

            assertThat(cut.updateOrder(updateOrderPayload, client).statusCode.is2xxSuccessful)
        }
    }

    @Test
    fun `update order which doesn't exist throws`() {
        every { db.findByHostNameAndHostOrderId(op.hostName, op.hostOrderId) } returns Mono.empty()

        assertThatExceptionOfType(ResponseStatusException::class.java).isThrownBy {
            runTest {
                cut.updateOrder(updateOrderPayload, client)
            }
        }.withMessageContaining("does not exist")
    }

    @Test
    fun `update order which is being processed is conflict`() {
        every { db.findByHostNameAndHostOrderId(op.hostName, op.hostOrderId) } returns
            Mono.just(
                op.toOrder().copy(status = OrderStatus.IN_PROGRESS)
            )
        coEvery { synq.updateOrder(any()) } returns ResponseEntity.notFound().build()

        assertThatExceptionOfType(ResponseStatusException::class.java).isThrownBy {
            runTest {
                cut.updateOrder(updateOrderPayload, client)
            }
        }.withMessageContaining("409 CONFLICT")
    }

    @Test
    fun `update order which you don't own throws`() {
        assertThatExceptionOfType(ResponseStatusException::class.java).isThrownBy {
            runBlocking {
                cut.updateOrder(updateOrderPayload, "Alma")
            }
        }.withMessageContaining("403 FORBIDDEN")
    }

    @Test
    fun `update order which doesn't exist in synq throws`() {
        every { db.findByHostNameAndHostOrderId(op.hostName, op.hostOrderId) } returns Mono.just(op.toOrder())
        coEvery { synq.updateOrder(any()) } throws ServerErrorException("Not found", null)

        assertThatExceptionOfType(ServerErrorException::class.java).isThrownBy {
            runTest {
                cut.updateOrder(updateOrderPayload, client)
            }
        }
    }

// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// Test Help //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    /**
     * Used for testing regular order functionality (op = order payload)
     */

    private val op =
        ApiOrderPayload(
            orderId = "axiell-order-69",
            hostName = HostName.AXIELL,
            hostOrderId = "axiell-order-69",
            status = OrderStatus.NOT_STARTED,
            productLine = listOf(ProductLine("mlt-420", OrderLineStatus.NOT_STARTED)),
            orderType = OrderType.LOAN,
            owner = Owner.NB,
            receiver =
                OrderReceiver(
                    name = "name",
                    address = "address",
                    postalCode = "postalCode",
                    city = "city",
                    phoneNumber = "phoneNumber",
                    location = "location"
                ),
            callbackUrl = "callbackUrl"
        )

    /**
     * Used for testing order update functionality
     */
    private val updateOrderPayload =
        ApiUpdateOrderPayload(
            hostName = HostName.AXIELL,
            hostOrderId = "axiell-order-69",
            productLine = listOf(ProductLine("mlt-420", OrderLineStatus.NOT_STARTED)),
            orderType = OrderType.LOAN,
            receiver =
                OrderReceiver(
                    name = "name",
                    address = "address",
                    postalCode = "postalCode",
                    city = "city",
                    phoneNumber = "phoneNumber",
                    location = "location"
                ),
            callbackUrl = "callbackUrl"
        )

    private val client = HostName.AXIELL.name

    private fun <T : Throwable> assertExceptionThrownWithMessage(
        payload: ApiOrderPayload,
        message: String,
        exception: Class<T>
    ) = assertThatExceptionOfType(exception).isThrownBy {
        runBlocking { cut.createOrder(payload, client) }
    }.withMessageContaining(message)
}
