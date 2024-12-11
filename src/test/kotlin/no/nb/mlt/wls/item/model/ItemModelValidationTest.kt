package no.nb.mlt.wls.item.model

import no.nb.mlt.wls.application.hostapi.item.ApiItemPayload
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging
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
                quantity = null,
                location = null,
                callbackUrl = null
            )

        thenCode(item::validate).doesNotThrowAnyException()
    }

    @Test
    fun `item with blank hostId should fail validation`() {
        // Given
        val item = validItem.copy(hostId = "")

        // When
        val thrown = catchThrowable(item::validate)

        // Then
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

// TODO - Since this is handled by Spring, remove?
//    @Test
//    fun `item with blank itemCategory should fail validation`() {
//        val item = validItem.copy(itemCategory = "")
//
//        val thrown = catchThrowable(item::validate)
//
//        then(thrown)
//            .isNotNull()
//            .isInstanceOf(ValidationException::class.java)
//            .hasMessageContaining("category")
//    }

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
        val item = validItem.copy(callbackUrl = "hppt://callback.com/item")

        val thrown = catchThrowable(item::validate)

        then(thrown)
            .isNotNull()
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("callback URL")
    }

// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// Test Help //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    private val validItem =
        ApiItemPayload(
            hostId = "mlt-test-1234",
            hostName = HostName.AXIELL,
            description = "Tyven skal du hete",
            itemCategory = ItemCategory.PAPER,
            preferredEnvironment = Environment.NONE,
            packaging = Packaging.NONE,
            owner = Owner.NB,
            callbackUrl = "https://callback.com/item",
            location = "location",
            quantity = 1
        )
}
