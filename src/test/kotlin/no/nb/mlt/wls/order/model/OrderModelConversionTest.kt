package no.nb.mlt.wls.order.model

import no.nb.mlt.wls.domain.HostName
import no.nb.mlt.wls.domain.Owner
import no.nb.mlt.wls.infrastructure.synq.SynqOwner
import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.payloads.SynqOrderPayload
import no.nb.mlt.wls.order.payloads.toApiOrderPayload
import no.nb.mlt.wls.order.payloads.toOrder
import no.nb.mlt.wls.order.payloads.toSynqPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OrderModelConversionTest {
    private val testApiOrderPayload =
        ApiOrderPayload(
            orderId = "hostOrderId",
            hostName = HostName.AXIELL,
            hostOrderId = "hostOrderId",
            status = OrderStatus.NOT_STARTED,
            productLine = listOf(ProductLine("hostProductId", OrderLineStatus.NOT_STARTED)),
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

    private val testOrder =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "hostOrderId",
            status = OrderStatus.NOT_STARTED,
            productLine = listOf(ProductLine("hostProductId", OrderLineStatus.NOT_STARTED)),
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

    private val testSynqOrderPayload =
        SynqOrderPayload(
            orderId = "hostOrderId",
            orderType = SynqOrderPayload.SynqOrderType.STANDARD,
            dispatchDate = LocalDateTime.now(),
            orderDate = LocalDateTime.now(),
            priority = 1,
            owner = SynqOwner.NB,
            orderLine = listOf(SynqOrderPayload.OrderLine(1, "hostProductId", 1.0))
        )

    @Test
    fun `order converts to API payload`() {
        val payload = testOrder.toApiOrderPayload()

        assertThat(payload.orderId).isEqualTo(testApiOrderPayload.orderId)
        assertThat(payload.hostName).isEqualTo(testApiOrderPayload.hostName)
        assertThat(payload.hostOrderId).isEqualTo(testApiOrderPayload.hostOrderId)
        assertThat(payload.status).isEqualTo(testApiOrderPayload.status)
        assertThat(payload.productLine).isEqualTo(testApiOrderPayload.productLine)
        assertThat(payload.orderType).isEqualTo(testApiOrderPayload.orderType)
        assertThat(payload.owner).isEqualTo(testApiOrderPayload.owner)
        assertThat(payload.receiver).isEqualTo(testApiOrderPayload.receiver)
        assertThat(payload.callbackUrl).isEqualTo(testApiOrderPayload.callbackUrl)
    }

    @Test
    fun `order converts to SynQ payload`() {
        val synqPayload = testOrder.toSynqPayload()

        // Dates are not compared as they are generated in the function
        assertThat(synqPayload.orderId).isEqualTo(testSynqOrderPayload.orderId)
        assertThat(synqPayload.orderType).isEqualTo(testSynqOrderPayload.orderType)
        assertThat(synqPayload.priority).isEqualTo(testSynqOrderPayload.priority)
        assertThat(synqPayload.owner).isEqualTo(testSynqOrderPayload.owner)
        assertThat(synqPayload.orderLine).isEqualTo(testSynqOrderPayload.orderLine)
    }

    @Test
    fun `API payload converts to order`() {
        val order = testApiOrderPayload.toOrder()

        assertThat(order.hostName).isEqualTo(testOrder.hostName)
        assertThat(order.hostOrderId).isEqualTo(testOrder.hostOrderId)
        assertThat(order.status).isEqualTo(testOrder.status)
        assertThat(order.productLine).isEqualTo(testOrder.productLine)
        assertThat(order.orderType).isEqualTo(testOrder.orderType)
        assertThat(order.owner).isEqualTo(testOrder.owner)
        assertThat(order.receiver).isEqualTo(testOrder.receiver)
        assertThat(order.callbackUrl).isEqualTo(testOrder.callbackUrl)
    }
}
