package no.nb.mlt.wls.order.model

import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.ApiUpdateOrderPayload
import no.nb.mlt.wls.application.hostapi.order.OrderLine
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.api.BDDAssertions.then
import org.assertj.core.api.BDDAssertions.thenCode
import org.junit.jupiter.api.Test

class OrderModelValidationTest {
// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// OrderLine //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    @Test
    fun `valid orderLine should pass validation`() {
        thenCode(validOrderLine::validate).doesNotThrowAnyException()
    }

    @Test
    fun `orderLine with blank hostId should fail validation`() {
        val orderLine = validOrderLine.copy(hostId = "")

        val thrown = catchThrowable(orderLine::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("hostId")
    }

// /////////////////////////////////////////////////////////////////////////////
// ///////////////////////////////// Address ///////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    // TODO - Add Address field tests

// /////////////////////////////////////////////////////////////////////////////
// ////////////////////////////////// Order ////////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    @Test
    fun `valid order should pass validation`() {
        thenCode(validOrder::validate).doesNotThrowAnyException()
    }

    @Test
    fun `order with blank hostOrderId should fail validation`() {
        val order = validOrder.copy(hostOrderId = "")

        val thrown = catchThrowable(order::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("hostOrderId")
    }

    @Test
    fun `order with empty orderLine should fail validation`() {
        val order = validOrder.copy(orderLine = emptyList())

        val thrown = catchThrowable(order::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("order line")
    }

    @Test
    fun `order with invalid callback URL should fail validation`() {
        val order = validOrder.copy(callbackUrl = "")

        val thrown = catchThrowable(order::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("callback URL")
    }

    @Test
    fun `order with invalid orderLine should fail validation`() {
        val order = validOrder.copy(orderLine = listOf(OrderLine(hostId = "", status = null)))

        val thrown = catchThrowable(order::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("order line's")
    }

    // TODO - Test for address

// /////////////////////////////////////////////////////////////////////////////
// /////////////////////////////// OrderUpdate // //////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    @Test
    fun `valid update order should pass validation`() {
        thenCode(validUpdateOrderPayload::validate).doesNotThrowAnyException()
    }

    @Test
    fun `update order with blank hostOrderId should fail validation`() {
        val order = validUpdateOrderPayload.copy(hostOrderId = "")

        val thrown = catchThrowable(order::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("hostOrderId")
    }

    @Test
    fun `update order with empty orderLine should fail validation`() {
        val order = validUpdateOrderPayload.copy(orderLine = emptyList())

        val thrown = catchThrowable(order::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("order line")
    }

    @Test
    fun `update order with invalid callback URL should fail validation`() {
        val order = validUpdateOrderPayload.copy(callbackUrl = "")

        val thrown = catchThrowable(order::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("callback URL")
    }

    @Test
    fun `update order with invalid orderLine should fail validation`() {
        val order = validUpdateOrderPayload.copy(orderLine = listOf(OrderLine(hostId = "", status = null)))

        val thrown = catchThrowable(order::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("order line's")
    }

    // TODO - Test Address

// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// Test Help //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    val validOrderLine = OrderLine("item-123", Order.OrderItem.Status.NOT_STARTED)

    val validAddress =
        Order.Address(
            name = "real name",
            addressLine1 = "real street",
            addressLine2 = null,
            zipcode = "12345-WA",
            city = "england",
            state = "cornwall"
        )

    val validOrder =
        ApiOrderPayload(
            hostName = HostName.AXIELL,
            hostOrderId = "order-123",
            status = Order.Status.NOT_STARTED,
            orderLine = listOf(validOrderLine),
            orderType = Order.Type.LOAN,
            address = validAddress,
            contactPerson = "contactPerson",
            callbackUrl = "https://callback.com/order"
        )

    val validUpdateOrderPayload =
        ApiUpdateOrderPayload(
            hostName = HostName.AXIELL,
            hostOrderId = "order-123",
            orderLine = listOf(validOrderLine),
            orderType = Order.Type.LOAN,
            contactPerson = "contactPerson",
            address = validAddress,
            callbackUrl = "https://callback.com/order"
        )
}
