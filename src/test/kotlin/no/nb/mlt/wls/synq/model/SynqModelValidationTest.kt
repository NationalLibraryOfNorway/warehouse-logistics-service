package no.nb.mlt.wls.synq.model

import no.nb.mlt.wls.application.synqapi.synq.AttributeValue
import no.nb.mlt.wls.application.synqapi.synq.OrderLine
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderPickingConfirmationPayload
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatus
import no.nb.mlt.wls.application.synqapi.synq.SynqOrderStatusUpdatePayload
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import no.nb.mlt.wls.infrastructure.synq.toSynqHostname
import org.assertj.core.api.BDDAssertions.catchThrowable
import org.assertj.core.api.BDDAssertions.then
import org.assertj.core.api.BDDAssertions.thenCode
import org.junit.jupiter.api.Test

class SynqModelValidationTest {
    @Test
    fun `valid SynqOrderStatusUpdatePayload should pass validation`() {
        thenCode(validSynqOrderStatusUpdatePayload::validate).doesNotThrowAnyException()
    }

    @Test
    fun `SynqOrderStatusUpdatePayload with blank warehouse should fail validation`() {
        val payload = validSynqOrderStatusUpdatePayload.copy(warehouse = "")

        val thrown = catchThrowable(payload::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("warehouse")
    }

    @Test
    fun `valid SynqOrderPickingConfirmationPayload should pass validation`() {
        thenCode(validSynqOrderStatusUpdatePayload::validate).doesNotThrowAnyException()
    }

    @Test
    fun `SynqOrderPickingConfirmationPayload with no order line should fail validation`() {
        val payload = validSynqOrderPickingConfirmationPayload.copy(orderLine = emptyList())

        val thrown = catchThrowable(payload::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("order line")
    }

    @Test
    fun `SynqOrderPickingConfirmationPayload with blank operator should fail validation`() {
        val payload = validSynqOrderPickingConfirmationPayload.copy(operator = "")

        val thrown = catchThrowable(payload::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("operator")
    }

    @Test
    fun `SynqOrderPickingConfirmationPayload with blank warehouse should fail validation`() {
        val payload = validSynqOrderPickingConfirmationPayload.copy(warehouse = "")

        val thrown = catchThrowable(payload::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("warehouse")
    }

    @Test
    fun `SynqOrderPickingConfirmationPayload with invalid order line should fail validation`() {
        val payload =
            validSynqOrderPickingConfirmationPayload.copy(
                orderLine =
                    listOf(
                        validSynqOrderLine1.copy(productId = "")
                    )
            )

        val thrown = catchThrowable(payload::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("product ID")
    }

    @Test
    fun `OrderLine with blank hostName should fail validation`() {
        val orderLine = validSynqOrderLine1.copy(hostName = "")

        val thrown = catchThrowable(orderLine::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContainingAll("host name", "blank")
    }

    @Test
    fun `OrderLine with invalid hostName should fail validation`() {
        val orderLine = validSynqOrderLine1.copy(hostName = "invalid")

        val thrown = catchThrowable(orderLine::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("host name: 'invalid'")
    }

    @Test
    fun `OrderLine with negative orderLineNumber should fail validation`() {
        val orderLine = validSynqOrderLine1.copy(orderLineNumber = -1)

        val thrown = catchThrowable(orderLine::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("line number")
    }

    @Test
    fun `OrderLine with blank orderTuId should fail validation`() {
        val orderLine = validSynqOrderLine1.copy(orderTuId = "")

        val thrown = catchThrowable(orderLine::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("TU ID")
    }

    private val validSynqOrderStatusUpdatePayload =
        SynqOrderStatusUpdatePayload(
            prevStatus = SynqOrderStatus.PICKED,
            status = SynqOrderStatus.COMPLETED,
            hostName = toSynqHostname(HostName.AXIELL),
            warehouse = "Sikringmagasin_2"
        )

    private val validSynqOrderLine1 =
        OrderLine(
            confidentialProduct = false,
            hostName = toSynqHostname(HostName.AXIELL),
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
            hostName = toSynqHostname(HostName.AXIELL),
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
