package no.nb.mlt.wls.item.model

import no.nb.mlt.wls.application.hostapi.item.toApiPayload
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.api.BDDAssertions.then
import org.assertj.core.api.BDDAssertions.thenCode
import org.junit.jupiter.api.Test

class ItemModelValidationTest {
    @Test
    fun `valid item should pass validation`() {
        thenCode(validItem::validate).doesNotThrowAnyException()
    }

    @Test
    fun `item with minimal valid data should pass validation`() {
        val item =
            validItem.copy(
                quantity = 0,
                location = UNKNOWN_LOCATION,
                callbackUrl = null
            )

        thenCode(item::validate).doesNotThrowAnyException()
    }

    @Test
    fun `item with blank hostId should fail validation`() {
        val item = validItem.copy(hostId = "")

        val thrown = catchThrowable(item::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("hostId")
    }

    @Test
    fun `item with blank description should fail validation`() {
        val item = validItem.copy(description = "")

        val thrown = catchThrowable(item::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("description")
    }

    @Test
    fun `item with blank location should fail validation`() {
        val item = validItem.copy(location = "")

        val thrown = catchThrowable(item::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("location")
    }

    @Test
    fun `item with negative quantity should fail validation`() {
        val item = validItem.copy(quantity = -1)

        val thrown = catchThrowable(item::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("quantity")
    }

    @Test
    fun `item with invalid callbackUrl should fail validation`() {
        val item = validItem.copy(callbackUrl = "hppts://invalid-callback-wls.no/item")

        val thrown = catchThrowable(item::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("callback URL")
    }

    private val testItem = createTestItem()

    private val validItem = testItem.toApiPayload()
}
