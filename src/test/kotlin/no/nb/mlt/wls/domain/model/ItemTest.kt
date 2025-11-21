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
        assertThat(testItem).isEqualTo(expectedItem)
    }

    @Test
    fun `synchronizing item should update if new quantity is bigger than zero`() {
        val testItem = createTestItem(quantity = 0, location = "UNKNOWN", associatedStorage = AssociatedStorage.UNKNOWN)
        testItem.synchronizeItem(1, "SYNQ_WAREHOUSE", AssociatedStorage.SYNQ)

        assertThat(testItem.associatedStorage).isEqualTo(AssociatedStorage.SYNQ)
        assertThat(testItem.location).isEqualTo("SYNQ_WAREHOUSE")
        assertThat(testItem.quantity).isEqualTo(1)
    }

    @Test
    fun `editing item with same data returns same item`() {
        val item = createTestItem()

        val editedItem =
            item.edit(
                description = item.description,
                itemCategory = item.itemCategory,
                preferredEnvironment = item.preferredEnvironment,
                packaging = item.packaging,
                callbackUrl = item.callbackUrl
            )

        assertThat(editedItem.equalsExactly(item)).isTrue()
    }

    @Test
    fun `editing item with partially new data returns edited item`() {
        val item = createTestItem()

        val editedItem =
            item.edit(
                description = "new description",
                itemCategory = item.itemCategory,
                preferredEnvironment = item.preferredEnvironment,
                packaging = item.packaging,
                callbackUrl = item.callbackUrl
            )

        assertThat(editedItem.equalsExactly(item)).isFalse()
        assertThat(editedItem.description).isEqualTo("new description")
    }

    @Test
    fun `editing item with wholly new data returns edited item`() {
        val item = createTestItem()

        val editedItem =
            item.edit(
                description = "new description",
                itemCategory = ItemCategory.FILM,
                preferredEnvironment = Environment.FREEZE,
                packaging = Packaging.BOX,
                callbackUrl = "https://callback-wls.no/v2/item"
            )

        assertThat(editedItem.equalsExactly(item)).isFalse()
        assertThat(editedItem.description).isEqualTo("new description")
        assertThat(editedItem.itemCategory).isEqualTo(ItemCategory.FILM)
        assertThat(editedItem.preferredEnvironment).isEqualTo(Environment.FREEZE)
        assertThat(editedItem.packaging).isEqualTo(Packaging.BOX)
        assertThat(editedItem.callbackUrl).isEqualTo("https://callback-wls.no/v2/item")
    }
}
