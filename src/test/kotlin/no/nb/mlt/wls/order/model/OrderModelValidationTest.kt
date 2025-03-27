package no.nb.mlt.wls.order.model

import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.ApiUpdateOrderPayload
import no.nb.mlt.wls.application.hostapi.order.OrderLine
import no.nb.mlt.wls.application.hostapi.order.toApiPayload
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import no.nb.mlt.wls.testOrder
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.api.BDDAssertions.then
import org.assertj.core.api.BDDAssertions.thenCode
import org.junit.jupiter.api.Test

class OrderModelValidationTest {


////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////  Test Setup  /////////////////////////////////
////////////////////////////////////////////////////////////////////////////////


    @Test
    fun `valid orderLine should pass validation`() {
        thenCode(validOrderLine::validate).doesNotThrowAnyException()
    }

    @Test
    fun `orderLine with blank hostId should fail validation`() {
        val orderLine = validOrderLine.copy(hostId = "")

        val thrown = catchThrowable(orderLine::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("hostId")
    }

    @Test
    fun `valid address should pass validation`() {
        thenCode(validAddress::validate).doesNotThrowAnyException()
    }

    @Test
    fun `address with blank fields should fail validation`() {
        val invalidAddress = validAddress.copy(recipient = "")
        val invalidCityAddress = validAddress.copy(city = "")

        val error = catchThrowable(invalidAddress::validate)
        then(error).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("recipient must not")

        val anotherError = catchThrowable(invalidCityAddress::validate)
        then(anotherError).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("city must not")
    }

    @Test
    fun `valid order should pass validation`() {
        thenCode(validOrder::validate).doesNotThrowAnyException()
    }

    @Test
    fun `order with blank hostOrderId should fail validation`() {
        val order = validOrder.copy(hostOrderId = "")

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("hostOrderId")
    }

    @Test
    fun `order with empty orderLine should fail validation`() {
        val order = validOrder.copy(orderLine = emptyList())

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("order line")
    }

    @Test
    fun `order with invalid callback URL should fail validation`() {
        val order = validOrder.copy(callbackUrl = "")

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("callback URL")
    }

    @Test
    fun `order with invalid orderLine should fail validation`() {
        val order = validOrder.copy(orderLine = listOf(OrderLine(hostId = "", status = null)))

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("order line's")
    }

    @Test
    fun `order with invalid address should fail validation`() {
        val order = validOrder.copy(address = validAddress.copy(recipient = ""))

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("Invalid address")
    }

    @Test
    fun `valid update order should pass validation`() {
        thenCode(validUpdateOrderPayload::validate).doesNotThrowAnyException()
    }

    @Test
    fun `update order with blank hostOrderId should fail validation`() {
        val order = validUpdateOrderPayload.copy(hostOrderId = "")

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("hostOrderId")
    }

    @Test
    fun `update order with empty orderLine should fail validation`() {
        val order = validUpdateOrderPayload.copy(orderLine = emptyList())

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("order line")
    }

    @Test
    fun `update order with invalid callback URL should fail validation`() {
        val order = validUpdateOrderPayload.copy(callbackUrl = "")

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("callback URL")
    }

    @Test
    fun `update order with invalid orderLine should fail validation`() {
        val order = validUpdateOrderPayload.copy(orderLine = listOf(OrderLine(hostId = "", status = null)))

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("order line's")
    }

    @Test
    fun `update order with invalid address should fail validation`() {
        val order = validUpdateOrderPayload.copy(address = validAddress?.copy(recipient = ""))

        val thrown = catchThrowable(order::validate)

        then(thrown).isNotNull().isInstanceOf(ValidationException::class.java).hasMessageContaining("address")
    }

    val validOrderLine = testOrder.orderLine

    val validAddress = testOrder.address

    val validOrder = testOrder.toApiPayload()

    val validUpdateOrderPayload = ApiUpdateOrderPayload(
        hostName = validOrder.hostName,
        hostOrderId = validOrder.hostOrderId,
        orderLine = validOrder.orderLine,
        orderType = validOrder.orderType,
        contactPerson = validOrder.contactPerson,
        contactEmail = validOrder.contactEmail,
        address = validOrder.address,
        callbackUrl = validOrder.callbackUrl,
        note = validOrder.note
    )
}
