package no.nb.mlt.wls.domain.model

import no.nb.mlt.wls.createTestItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ItemTest {
    @Test
    fun `synchronizeItem should only update item when item exists within another storage system`() {
        val expectedItem = createTestItem(quantity = 1, location = "SOMEWHERE_IN_KARDEX", associatedStorage = AssociatedStorage.KARDEX)
        val testItem = createTestItem(quantity = 1, location = "SOMEWHERE_IN_KARDEX", associatedStorage = AssociatedStorage.KARDEX)
        testItem.synchronizeItem(0, "MISSING", AssociatedStorage.SYNQ)
        assertThat(testItem.associatedStorage).isNotEqualTo(AssociatedStorage.SYNQ)
        assertThat(testItem.associatedStorage).isEqualTo(expectedItem)
    }
}
