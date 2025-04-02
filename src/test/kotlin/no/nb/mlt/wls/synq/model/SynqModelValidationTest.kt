package no.nb.mlt.wls.synq.model

import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import no.nb.mlt.wls.application.synqapi.synq.AttributeValue
import no.nb.mlt.wls.application.synqapi.synq.OrderLine
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderPickingConfirmationPayload
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatus
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatusUpdatePayload
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.assertj.core.api.BDDAssertions.catchThrowable
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SynqModelValidationTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun beforeEach() {
        val factory: ValidatorFactory = Validation.buildDefaultValidatorFactory()
        validator = factory.validator
    }

    @Test
    fun `valid SynqOrderStatusUpdatePayload should pass validation`() {
        assert(validator.validate(validSynqOrderStatusUpdatePayload).isEmpty())
    }

    @Test
    fun `SynqOrderStatusUpdatePayload with blank warehouse should fail validation`() {
        val invalidPayload = validSynqOrderStatusUpdatePayload.copy(warehouse = "")
        assert(validator.validate(invalidPayload).isNotEmpty())
    }

    @Test
    fun `valid SynqOrderPickingConfirmationPayload should pass validation`() {
        assert(validator.validate(validSynqOrderPickingConfirmationPayload).isEmpty())
    }

    @Test
    fun `SynqOrderPickingConfirmationPayload with no order line should fail validation`() {
        val invalidPayload = validSynqOrderPickingConfirmationPayload.copy(orderLine = emptyList())
        assert(validator.validate(invalidPayload).isNotEmpty())
    }

    @Test
    fun `SynqOrderPickingConfirmationPayload with blank operator should fail validation`() {
        val invalidPayload = validSynqOrderPickingConfirmationPayload.copy(operator = "")
        assert(validator.validate(invalidPayload).isNotEmpty())
    }

    @Test
    fun `SynqOrderPickingConfirmationPayload with blank warehouse should fail validation`() {
        val invalidPayload = validSynqOrderPickingConfirmationPayload.copy(warehouse = "")
        assert(validator.validate(invalidPayload).isNotEmpty())
    }

    @Test
    fun `SynqOrderPickingConfirmationPayload with invalid order line should fail validation`() {
        val invalidPayload =
            validSynqOrderPickingConfirmationPayload.copy(
                orderLine =
                    listOf(
                        validSynqOrderLine1.copy(productId = "")
                    )
            )
        assert(validator.validate(invalidPayload).isNotEmpty())
    }

    @Test
    fun `OrderLine with blank hostName should fail validation`() {
        val orderLine = validSynqOrderLine1.copy(hostName = "")
        assert(validator.validate(orderLine).isNotEmpty())

    }

    @Test
    fun `OrderLine with invalid hostName should fail validation`() {
        // TODO - this check doesn't detect invalid hostnames
        val orderLine = validSynqOrderLine1.copy(hostName = "invalid")
        assert(validator.validate(orderLine).isEmpty())
        // manual validation does
        val thrown = catchThrowable(orderLine::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("host name")
    }

    @Test
    fun `OrderLine with negative orderLineNumber should fail validation`() {
        val orderLine = validSynqOrderLine1.copy(orderLineNumber = -1)
        assert(validator.validate(orderLine).isNotEmpty())
    }

    @Test
    fun `OrderLine with blank orderTuId should fail validation`() {
        val orderLine = validSynqOrderLine1.copy(orderTuId = "")
        assert(validator.validate(orderLine).isNotEmpty())
    }

    private val validSynqOrderStatusUpdatePayload =
        SynqOrderStatusUpdatePayload(
            prevStatus = SynqOrderStatus.PICKED,
            status = SynqOrderStatus.COMPLETED,
            hostName = HostName.AXIELL,
            warehouse = "Sikringmagasin_2"
        )

    private val validSynqOrderLine1 =
        OrderLine(
            confidentialProduct = false,
            hostName = "AXIELL",
            orderLineNumber = 1,
            orderTuId = "6942066642",
            orderTuType = "UFO",
            productId = "testItem-01",
            productVersionId = "1.0",
            quantity = 1,
            attributeValue =
                listOf(
                    AttributeValue(
                        name = "materialStatus",
                        value = "Available"
                    )
                )
        )

    private val validSynqOrderLine2 =
        OrderLine(
            confidentialProduct = false,
            hostName = "AXIELL",
            orderLineNumber = 2,
            orderTuId = "6942066642",
            orderTuType = "UFO",
            productId = "testItem-02",
            productVersionId = "1.0",
            quantity = 1,
            attributeValue =
                listOf(
                    AttributeValue(
                        name = "materialStatus",
                        value = "Available"
                    )
                )
        )

    private val validSynqOrderPickingConfirmationPayload =
        SynqOrderPickingConfirmationPayload(
            orderLine = listOf(validSynqOrderLine1, validSynqOrderLine2),
            operator = "per.person@nb.no",
            warehouse = "Sikringmagasin_2"
        )
}
