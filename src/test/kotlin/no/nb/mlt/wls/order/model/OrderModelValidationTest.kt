package no.nb.mlt.wls.order.model

import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import no.nb.mlt.wls.application.hostapi.order.OrderLine
import no.nb.mlt.wls.application.hostapi.order.toApiPayload
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.Order
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class OrderModelValidationTest {
    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        val factory: ValidatorFactory = Validation.buildDefaultValidatorFactory()
        validator = factory.validator
    }

    @Test
    fun `valid orderLine should pass validation`() {
        assertThat(validator.validate(validOrderLine)).isEmpty()
    }

    @Test
    fun `orderLine with blank hostId should fail validation`() {
        val orderLine = validOrderLine.copy(hostId = "")
        assertThat(validator.validate(orderLine)).isNotEmpty
    }

    @Test
    fun `valid address should pass validation`() {
        assert(validator.validate(validAddress).isEmpty())
    }

    @Test
    fun `address with blank fields should fail validation`() {
        val invalidAddress = validOrderPayloadAddress.copy(recipient = "", city = "")
        val validationErrors = validator.validate(invalidAddress)
        assertThat(validationErrors)
            .isNotEmpty
            .hasSize(2)
    }

    @Test
    fun `valid order should pass validation`() {
        assertThat(validator.validate(validOrder)).isEmpty()
    }

    @Test
    fun `order with blank hostOrderId should fail validation`() {
        val invalidOrder = validOrder.copy(hostOrderId = "")
        assertThat(validator.validate(invalidOrder)).isNotEmpty
    }

    @Test
    fun `order with empty orderLine should fail validation`() {
        val invalidOrder = validOrder.copy(orderLine = emptyList())
        assertThat(validator.validate(invalidOrder)).isNotEmpty
    }

    @Test
    fun `order with invalid callback URL should fail validation`() {
        val invalidOrder = validOrder.copy(callbackUrl = "")
        assertThat(validator.validate(invalidOrder)).isNotEmpty
    }

    @Test
    fun `order with invalid orderLine should fail validation`() {
        val invalidOrder = validOrder.copy(orderLine = listOf(OrderLine(hostId = "", status = null)))
        assertThat(validator.validate(invalidOrder)).isNotEmpty
    }

    @Test
    fun `order with invalid address should fail validation`() {
        val invalidOrder = validOrder.copy(address = validOrderPayloadAddress.copy(recipient = ""))
        assertThat(validator.validate(invalidOrder)).isNotEmpty
    }

    @Test
    fun `order with all failed or picked lines should be marked as COMPLETED`() {
        val order = testOrder.copy().pick(listOf(testOrder.orderLine.last().hostId))
        val failedOrder = order.cancelLines(listOf(testOrder.orderLine.first().hostId))
        assertThat(failedOrder.status).isEqualTo(Order.Status.COMPLETED)
    }

    @Test
    fun `order with all failed lines should be marked as DELETED`() {
        val order = testOrder.copy()
        val failedOrder = order.cancelLines(testOrder.orderLine.map { it.hostId })
        assertThat(failedOrder.status).isEqualTo(Order.Status.DELETED)
    }

    @Test
    fun `order in progress where all lines fail should be marked as DELETED`() {
        val order = testOrder.copy(status = Order.Status.IN_PROGRESS)
        assertDoesNotThrow {
            val updatedOrder = order.cancelLines(order.orderLine.map { it.hostId })
            assertThat(updatedOrder.status).isEqualTo(Order.Status.DELETED)
        }
    }

    private val testItem = createTestItem()

    private val testOrder = createTestOrder()

    private val validOrderLine = OrderLine(testItem.hostId, Order.OrderItem.Status.NOT_STARTED)

    // Can safely use address since testOrder object (should) have it set
    private val validAddress = testOrder.address!!

    private val validOrder = testOrder.toApiPayload()

    private val validOrderPayloadAddress = validOrder.address!!
}
