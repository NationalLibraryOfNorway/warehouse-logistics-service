package no.nb.mlt.wls.order.model

import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.toApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.toOrder
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
import no.nb.mlt.wls.infrastructure.synq.ShippingAddress
import no.nb.mlt.wls.infrastructure.synq.SynqOrderPayload
import no.nb.mlt.wls.infrastructure.synq.SynqOwner
import no.nb.mlt.wls.infrastructure.synq.toSynqPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OrderModelConversionTest {
    private val testApiOrderPayload =
        ApiOrderPayload(
            hostName = HostName.AXIELL,
            hostOrderId = "hostOrderId",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(),
            orderType = Order.Type.LOAN,
            owner = Owner.NB,
            receiver =
                Order.Receiver(
                    name = "name",
                    address = "address"
                ),
            callbackUrl = "callbackUrl"
        )

    private val testOrder =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "hostOrderId",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(Order.OrderItem("hostItemId", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            owner = Owner.NB,
            receiver =
                Order.Receiver(
                    name = "name",
                    address = "address"
                ),
            callbackUrl = "callbackUrl"
        )

    private val testSynqOrderPayload =
        SynqOrderPayload(
            orderId = "hostOrderId",
            orderType = SynqOrderPayload.SynqOrderType.STANDARD,
            dispatchDate = LocalDateTime.now(),
            orderDate = LocalDateTime.now(),
            priority = 5,
            owner = SynqOwner.NB,
            orderLine =
                listOf(
                    SynqOrderPayload.OrderLine(1, "hostItemId", 1.0)
                ),
            shippingAddress =
                ShippingAddress(
                    ShippingAddress.Address(
                        "contactPerson"
                    )
                )
        )

    @Test
    fun `order converts to API payload`() {
        val payload = testOrder.toApiOrderPayload()

        assertThat(payload.hostName).isEqualTo(testOrder.hostName)
        assertThat(payload.hostOrderId).isEqualTo(testOrder.hostOrderId)
        assertThat(payload.status).isEqualTo(testOrder.status)
        assertThat(payload.orderLine).isEqualTo(testOrder.orderLine)
        assertThat(payload.orderType).isEqualTo(testOrder.orderType)
        assertThat(payload.owner).isEqualTo(testOrder.owner)
        assertThat(payload.receiver).isEqualTo(testOrder.receiver)
        assertThat(payload.callbackUrl).isEqualTo(testOrder.callbackUrl)
    }

    @Test
    fun `order converts to Mongo Order payload`(){
        val mongoOrder = testOrder.toMongoOrder()

        assertThat(mongoOrder.hostName).isEqualTo(testOrder.hostName)
        assertThat(mongoOrder.hostOrderId).isEqualTo(testOrder.hostOrderId)
        assertThat(mongoOrder.status).isEqualTo(testOrder.status)
        assertThat(mongoOrder.orderLine).isEqualTo(testOrder.orderLine)
        assertThat(mongoOrder.orderType).isEqualTo(testOrder.orderType)
        assertThat(mongoOrder.owner).isEqualTo(testOrder.owner)
        assertThat(mongoOrder.receiver).isEqualTo(testOrder.receiver)
        assertThat(mongoOrder.callbackUrl).isEqualTo(testOrder.callbackUrl)
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

        assertThat(order.hostName).isEqualTo(testApiOrderPayload.hostName)
        assertThat(order.hostOrderId).isEqualTo(testApiOrderPayload.hostOrderId)
        assertThat(order.status).isEqualTo(testApiOrderPayload.status)
        assertThat(order.orderLine).isEqualTo(testApiOrderPayload.orderLine)
        assertThat(order.orderType).isEqualTo(testApiOrderPayload.orderType)
        assertThat(order.owner).isEqualTo(testApiOrderPayload.owner)
        assertThat(order.receiver).isEqualTo(testApiOrderPayload.receiver)
        assertThat(order.callbackUrl).isEqualTo(testApiOrderPayload.callbackUrl)
    }
}
