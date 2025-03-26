package no.nb.mlt.wls.order.model

import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.OrderLine
import no.nb.mlt.wls.application.hostapi.order.toApiOrderLine
import no.nb.mlt.wls.application.hostapi.order.toApiOrderPayload
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.infrastructure.callbacks.NotificationOrderPayload
import no.nb.mlt.wls.infrastructure.callbacks.toNotificationOrderPayload
import no.nb.mlt.wls.infrastructure.repositories.order.toMongoOrder
import no.nb.mlt.wls.infrastructure.synq.ShippingAddress
import no.nb.mlt.wls.infrastructure.synq.SynqOrderPayload
import no.nb.mlt.wls.infrastructure.synq.SynqOwner
import no.nb.mlt.wls.infrastructure.synq.toAutostorePayload
import no.nb.mlt.wls.infrastructure.synq.toSynqPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime

class OrderModelConversionTest {
    private val testApiOrderPayload =
        ApiOrderPayload(
            hostName = HostName.AXIELL,
            hostOrderId = "hostOrderId",
            status = null,
            orderLine = listOf(),
            orderType = Order.Type.LOAN,
            contactPerson = "contactPerson",
            contactEmail = "contact@ema.il",
            address =
                Order.Address(
                    recipient = "recipient",
                    addressLine1 = "address",
                    addressLine2 = "street",
                    postcode = "postcode",
                    city = "city",
                    region = "region",
                    country = "country"
                ),
            callbackUrl = "callbackUrl",
            note = "note"
        )

    private val testOrder =
        Order(
            hostName = HostName.AXIELL,
            hostOrderId = "hostOrderId",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(Order.OrderItem("hostItemId", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            contactPerson = "contactPerson",
            contactEmail = "contact@ema.il",
            address =
                Order.Address(
                    recipient = "recipient",
                    addressLine1 = "address",
                    addressLine2 = "street",
                    postcode = "postcode",
                    city = "city",
                    region = "region",
                    country = "country"
                ),
            callbackUrl = "callbackUrl",
            note = "note"
        )

    private val testSynqOrderPayload =
        SynqOrderPayload(
            orderId = "AXIELL-SD---hostOrderId",
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

    private val testSynqAutostoreOrderPayload =
        SynqOrderPayload(
            orderId = "AXIELL-AS---hostOrderId",
            orderType = SynqOrderPayload.SynqOrderType.AUTOSTORE,
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
            orderLine = listOf(NotificationOrderPayload.OrderLine("hostItemId", Order.OrderItem.Status.NOT_STARTED)),
            orderType = Order.Type.LOAN,
            address =
                Order.Address(
                    recipient = "recipient",
                    addressLine1 = "address",
                    addressLine2 = "street",
                    city = "city",
                    region = "region",
                    postcode = "postcode",
                    country = "country"
                ),
            contactPerson = "contactPerson",
            contactEmail = "contact@ema.il",
            note = "note",
            callbackUrl = "callbackUrl",
            eventTimestamp = Instant.now()
        )

    @Test
    fun `order converts to API payload`() {
        val payload = testOrder.toApiOrderPayload()

        assertThat(payload.hostName).isEqualTo(testOrder.hostName)
        assertThat(payload.hostOrderId).isEqualTo(testOrder.hostOrderId)
        assertThat(payload.status).isEqualTo(testOrder.status)
        assertThat(payload.orderLine[0].hostId).isEqualTo(testOrder.orderLine[0].hostId)
        assertThat(payload.orderType).isEqualTo(testOrder.orderType)
        assertThat(payload.contactPerson).isEqualTo(testOrder.contactPerson)
        assertThat(payload.address).isEqualTo(testOrder.address)
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
        assertThat(mongoOrder.contactPerson).isEqualTo(testOrder.contactPerson)
        assertThat(mongoOrder.address).isEqualTo(testOrder.address)
        assertThat(mongoOrder.callbackUrl).isEqualTo(testOrder.callbackUrl)
    }

    @Test
    fun `order converts to SynQ payload`() {
        val synqPayload = testOrder.toSynqPayload(SynqOrderPayload.SynqOrderType.STANDARD)

        // Dates are not compared as they are generated in the function
        assertThat(synqPayload.orderId).isEqualTo(testSynqOrderPayload.orderId)
        assertThat(synqPayload.orderType).isEqualTo(testSynqOrderPayload.orderType)
        assertThat(synqPayload.priority).isEqualTo(testSynqOrderPayload.priority)
        assertThat(synqPayload.owner).isEqualTo(testSynqOrderPayload.owner)
        assertThat(synqPayload.orderLine).isEqualTo(testSynqOrderPayload.orderLine)
    }

    @Test
    fun `order converts to SynQ autostore payload`() {
        val synqPayload = testOrder.toAutostorePayload()

        // Dates are not compared as they are generated in the function
        assertThat(synqPayload.orderId).isEqualTo(testSynqAutostoreOrderPayload.orderId)
        assertThat(synqPayload.orderType).isEqualTo(testSynqAutostoreOrderPayload.orderType)
        assertThat(synqPayload.priority).isEqualTo(testSynqAutostoreOrderPayload.priority)
        assertThat(synqPayload.owner).isEqualTo(testSynqAutostoreOrderPayload.owner)
        assertThat(synqPayload.orderLine).isEqualTo(testSynqAutostoreOrderPayload.orderLine)
    }

    @Test
    fun `order converts to notification payload`() {
        val testNotificationOrderPayload = testOrder.toNotificationOrderPayload(Instant.now())

        assertThat(testNotificationOrderPayload.hostName).isEqualTo(testOrderNotification.hostName)
        assertThat(testNotificationOrderPayload.hostOrderId).isEqualTo(testOrderNotification.hostOrderId)
        assertThat(testNotificationOrderPayload.status).isEqualTo(testOrderNotification.status)
        assertThat(testNotificationOrderPayload.orderLine[0].hostId).isEqualTo(testOrderNotification.orderLine[0].hostId)
        assertThat(testNotificationOrderPayload.orderType).isEqualTo(testOrderNotification.orderType)
        assertThat(testNotificationOrderPayload.address).isEqualTo(testOrderNotification.address)
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
        assertThat(order.contactPerson).isEqualTo(testApiOrderPayload.contactPerson)
        assertThat(order.address).isEqualTo(testApiOrderPayload.address)
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

    private fun ApiOrderPayload.toOrder() =
        Order(
            hostName = hostName,
            hostOrderId = hostOrderId,
            status = status ?: Order.Status.NOT_STARTED,
            orderLine = orderLine.map { it.toOrderItem() },
            orderType = orderType,
            contactPerson = contactPerson,
            contactEmail = contactEmail,
            address = address,
            callbackUrl = callbackUrl,
            note = note
        )
}
