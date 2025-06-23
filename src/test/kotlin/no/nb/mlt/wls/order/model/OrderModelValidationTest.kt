package no.nb.mlt.wls.order.model

import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import no.nb.mlt.wls.application.hostapi.order.OrderLine
import no.nb.mlt.wls.application.hostapi.order.toApiPayload
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.model.Order
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OrderModelValidationTest {
    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        val factory: ValidatorFactory = Validation.buildDefaultValidatorFactory()
        validator = factory.validator
    }

    @Test
    fun `valid orderLine should pass validation`() {
        assert(validator.validate(validOrderLine).isEmpty())
    }

    @Test
    fun `orderLine with blank hostId should fail validation`() {
        val orderLine = validOrderLine.copy(hostId = "")
        assert(validator.validate(orderLine).isNotEmpty())
    }

    @Test
    fun `valid address should pass validation`() {
        assert(validator.validate(validAddress).isEmpty())
    }

    @Test
    fun `address with blank fields should fail validation`() {
        val invalidAddress = validAddress.copy(recipient = "", city = "")
        val validationErrors = validator.validate(invalidAddress)
        assert(validationErrors.isNotEmpty())
        assert(validationErrors.size == 2)
    }

    @Test
    fun `valid order should pass validation`() {
        assert(validator.validate(validOrder).isEmpty())
    }

    @Test
    fun `order with blank hostOrderId should fail validation`() {
        val invalidOrder = validOrder.copy(hostOrderId = "")
        assert(validator.validate(invalidOrder).isNotEmpty())
    }

    @Test
    fun `order with empty orderLine should fail validation`() {
        val invalidOrder = validOrder.copy(orderLine = emptyList())
        assert(validator.validate(invalidOrder).isNotEmpty())
    }

    @Test
    fun `order with invalid callback URL should fail validation`() {
        val invalidOrder = validOrder.copy(callbackUrl = "")
        assert(validator.validate(invalidOrder).isNotEmpty())
    }

    @Test
    fun `order with invalid orderLine should fail validation`() {
        val invalidOrder = validOrder.copy(orderLine = listOf(OrderLine(hostId = "", status = null)))
        assert(validator.validate(invalidOrder).isNotEmpty())
    }

    @Test
    fun `order with invalid address should fail validation`() {
        val invalidOrder = validOrder.copy(address = validAddress.copy(recipient = ""))
        assert(validator.validate(invalidOrder).isNotEmpty())
    }

    private val testItem = createTestItem()

    private val testOrder = createTestOrder()

    private val validOrderLine = OrderLine(testItem.hostId, Order.OrderItem.Status.NOT_STARTED)

    // Can safely use address since testOrder object (should) have it set
    private val validAddress = testOrder.address!!

    private val validOrder = testOrder.toApiPayload()
}
