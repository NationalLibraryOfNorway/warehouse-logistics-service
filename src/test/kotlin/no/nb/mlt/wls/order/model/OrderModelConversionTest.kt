package no.nb.mlt.wls.order.model

import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.OrderLine
import no.nb.mlt.wls.application.hostapi.order.Receiver
import no.nb.mlt.wls.application.hostapi.order.toApiOrderLine
import no.nb.mlt.wls.application.hostapi.order.toApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.toReceiver
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.infrastructure.callbacks.NotificationOrderPayload
import no.nb.mlt.wls.infrastructure.callbacks.toNotificationOrderPayload
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
            status = null,
            orderLine = listOf(),
            orderType = Order.Type.LOAN,
            receiver = Receiver(name = "name", address = "address"),
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

    private val testOrderNotification =
        NotificationOrderPayload(
            hostName = HostName.AXIELL,
            hostOrderId = "hostOrderId",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(Order.OrderItem("hostItemId", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            owner = Owner.NB,
            receiver = Order.Receiver(name = "name", address = "address"),
            callbackUrl = "callbackUrl"
        )

    @Test
    fun `order converts to API payload`() {
        val payload = testOrder.toApiOrderPayload()

        assertThat(payload.hostName).isEqualTo(testOrder.hostName)
        assertThat(payload.hostOrderId).isEqualTo(testOrder.hostOrderId)
        assertThat(payload.status).isEqualTo(testOrder.status)
        assertThat(payload.orderLine[0].hostId).isEqualTo(testOrder.orderLine[0].hostId)
        assertThat(payload.orderType).isEqualTo(testOrder.orderType)
        assertThat(payload.receiver.name).isEqualTo(testOrder.receiver.name)
        assertThat(payload.callbackUrl).isEqualTo(testOrder.callbackUrl)
    }

    @Test
    fun `order converts to Mongo Order payload`() {
        val mongoOrder = testOrder.toMongoOrder()

        assertThat(mongoOrder.hostName).isEqualTo(testOrder.hostName)
        assertThat(mongoOrder.hostOrderId).isEqualTo(testOrder.hostOrderId)
        assertThat(mongoOrder.status).isEqualTo(testOrder.status)
        assertThat(mongoOrder.orderLine[0].hostId).isEqualTo(testOrder.orderLine[0].hostId)
        assertThat(mongoOrder.orderType).isEqualTo(testOrder.orderType)
        assertThat(mongoOrder.owner).isEqualTo(testOrder.owner)
        assertThat(mongoOrder.receiver.name).isEqualTo(testOrder.receiver.name)
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
    fun `order converts to notification payload`() {
        val testNotificationOrderPayload = testOrder.toNotificationOrderPayload()

        assertThat(testNotificationOrderPayload.hostName).isEqualTo(testOrderNotification.hostName)
        assertThat(testNotificationOrderPayload.hostOrderId).isEqualTo(testOrderNotification.hostOrderId)
        assertThat(testNotificationOrderPayload.status).isEqualTo(testOrderNotification.status)
        assertThat(testNotificationOrderPayload.orderLine[0].hostId).isEqualTo(testOrderNotification.orderLine[0].hostId)
        assertThat(testNotificationOrderPayload.orderType).isEqualTo(testOrderNotification.orderType)
        assertThat(testNotificationOrderPayload.owner).isEqualTo(testOrderNotification.owner)
        assertThat(testNotificationOrderPayload.receiver.name).isEqualTo(testOrderNotification.receiver.name)
        assertThat(testNotificationOrderPayload.callbackUrl).isEqualTo(testOrderNotification.callbackUrl)
    }

    @Test
    fun `API payload converts to order`() {
        val order = testApiOrderPayload.toOrder()

        assertThat(order.hostName).isEqualTo(testApiOrderPayload.hostName)
        assertThat(order.hostOrderId).isEqualTo(testApiOrderPayload.hostOrderId)
        assertThat(order.status).isEqualTo(Order.Status.NOT_STARTED)
        assertThat(order.orderLine).isEqualTo(testApiOrderPayload.orderLine)
        assertThat(order.orderType).isEqualTo(testApiOrderPayload.orderType)
        assertThat(order.receiver.name).isEqualTo(testApiOrderPayload.receiver.name)
        assertThat(order.callbackUrl).isEqualTo(testApiOrderPayload.callbackUrl)
    }

    private val testOrderLine =
        OrderLine(
            hostId = "hostItemId",
            status = null
        )

    private val testOrderItem =
        Order.OrderItem(
            hostId = "hostItemId",
            status = Order.OrderItem.Status.NOT_STARTED
        )

    @Test
    fun `OrderLine converts to OrderItem`() {
        val orderItem = testOrderLine.toOrderItem()

        assertThat(orderItem.hostId).isEqualTo(testOrderLine.hostId)
        assertThat(orderItem.status).isEqualTo(Order.OrderItem.Status.NOT_STARTED)

        val orderItem2 = testOrderLine.copy(status = Order.OrderItem.Status.PICKED).toOrderItem()

        assertThat(orderItem2.status).isEqualTo(Order.OrderItem.Status.PICKED)
    }

    @Test
    fun `OrderLine converts to CreateOrderItem`() {
        val createOrderItem = testOrderLine.toCreateOrderItem()

        assertThat(createOrderItem.hostId).isEqualTo(testOrderLine.hostId)
    }

    @Test
    fun `OrderItem converts to OrderLine`() {
        val orderLine = testOrderItem.toApiOrderLine()

        assertThat(orderLine.hostId).isEqualTo(testOrderItem.hostId)
        assertThat(orderLine.status).isEqualTo(testOrderItem.status)
    }

    private val testReceiver =
        Receiver(
            name = "name",
            address = "address"
        )

    private val testOrderReceiver =
        Order.Receiver(
            name = "name",
            address = "address"
        )

    @Test
    fun `Receiver converts to OrderReceiver`() {
        val orderReceiver = testReceiver.toOrderReceiver()

        assertThat(orderReceiver.name).isEqualTo(testReceiver.name)
        assertThat(orderReceiver.address).isEqualTo(testReceiver.address)

        val orderReceiver2 = testReceiver.copy(address = null).toOrderReceiver()

        assertThat(orderReceiver2.address).isEqualTo("")
    }

    @Test
    fun `OrderReceiver converts to Receiver`() {
        val receiver = testOrderReceiver.toReceiver()

        assertThat(receiver.name).isEqualTo(testOrderReceiver.name)
        assertThat(receiver.address).isEqualTo(testOrderReceiver.address)
    }

    private fun ApiOrderPayload.toOrder() =
        Order(
            hostName = hostName,
            hostOrderId = hostOrderId,
            status = status ?: Order.Status.NOT_STARTED,
            orderLine = orderLine.map { it.toOrderItem() },
            orderType = orderType,
            owner = Owner.NB,
            receiver = receiver.toOrderReceiver(),
            callbackUrl = callbackUrl
        )
}
