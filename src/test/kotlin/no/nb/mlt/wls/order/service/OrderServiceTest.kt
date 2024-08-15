package no.nb.mlt.wls.order.service

import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
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

class OrderServiceTest {

    @MockK
    private lateinit var db: OrderRepository

    @MockK
    private lateinit var synq: SynqOrderService

    @InjectMockKs
    private lateinit var cut: OrderService // cut = class under test



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
            receiver = OrderReceiver(
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
        cut.createOrder(payload)
    }.withMessageContaining(message)
}
