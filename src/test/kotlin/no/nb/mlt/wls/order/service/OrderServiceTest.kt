package no.nb.mlt.wls.order.service

import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.order.model.OrderLineStatus
import no.nb.mlt.wls.order.model.OrderReceiver
import no.nb.mlt.wls.order.model.OrderStatus
import no.nb.mlt.wls.order.model.OrderType
import no.nb.mlt.wls.order.model.ProductLine
import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.repository.OrderRepository
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.server.ServerWebInputException

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
        assertExceptionThrownWithMessage(top.copy(orderId = ""), "The order's orderId can not be blank", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(top.copy(orderId = "\t\n"), "The order's orderId can not be blank", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(top.copy(orderId = "      "), "The order's orderId can not be blank", ServerWebInputException::class.java)
    }

    @Test
    fun `save called with payload missing hostOrderId throws`() {
        assertExceptionThrownWithMessage(top.copy(hostOrderId = ""), "The order's hostOrderId is required", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(
            top.copy(hostOrderId = "\t\n"),
            "The order's hostOrderId is required",
            ServerWebInputException::class.java
        )
        assertExceptionThrownWithMessage(
            top.copy(hostOrderId = "      "),
            "The order's hostOrderId is required",
            ServerWebInputException::class.java
        )
    }

// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// Test Help //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    // Will be used in most tests (top = test order payload)
    private val top =
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

    private fun <T : Throwable> assertExceptionThrownWithMessage(
        payload: ApiOrderPayload,
        message: String,
        exception: Class<T>
    ) = assertThatExceptionOfType(exception).isThrownBy {
        runBlocking { cut.createOrder(payload) }
    }.withMessageContaining(message)
}
