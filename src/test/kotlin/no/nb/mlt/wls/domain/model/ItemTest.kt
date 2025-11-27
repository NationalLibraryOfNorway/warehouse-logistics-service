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
    fun `items are not equal when having different contents`() {
        val testItem = createTestItem(quantity = 0, location = "UNKNOWN", associatedStorage = AssociatedStorage.UNKNOWN)
        val syncItem = testItem.synchronizeItem(1, "SYNQ_WAREHOUSE", AssociatedStorage.SYNQ)
        val updatedItem = testItem.pick(1)

        assertThat(syncItem).isNotEqualTo(testItem)
        assertThat(updatedItem).isNotEqualTo(testItem)
    }

    @Test
    fun `items host or host id does not change despite different contents`() {
        val testItem = createTestItem(quantity = 0, location = "UNKNOWN", associatedStorage = AssociatedStorage.UNKNOWN)
        val syncItem = testItem.synchronizeItem(1, "SYNQ_WAREHOUSE", AssociatedStorage.SYNQ)
        val updatedItem = testItem.pick(1)

        assertThat(testItem.isSameItem(syncItem)).isTrue
        assertThat(testItem.isSameItem(updatedItem)).isTrue
    }

    @Test
    fun `synchronizing item should update if new quantity is bigger than zero`() {
        val testItem = createTestItem(quantity = 0, location = "UNKNOWN", associatedStorage = AssociatedStorage.UNKNOWN)
        val updatedItem = testItem.synchronizeItem(1, "SYNQ_WAREHOUSE", AssociatedStorage.SYNQ)

        assertThat(updatedItem.associatedStorage).isNotEqualTo(testItem.associatedStorage)
        assertThat(updatedItem.location).isNotEqualTo(testItem.location)
        assertThat(updatedItem.quantity).isNotEqualTo(testItem.quantity)
        assertThat(updatedItem.associatedStorage).isEqualTo(AssociatedStorage.SYNQ)
        assertThat(updatedItem.location).isEqualTo("SYNQ_WAREHOUSE")
        assertThat(updatedItem.quantity).isEqualTo(1)
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

        assertThat(editedItem).isEqualTo(item)
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

        assertThat(editedItem).isNotEqualTo(item)
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

        assertThat(editedItem).isNotEqualTo(item)
        assertThat(editedItem.isSameItem(item)).isTrue
        assertThat(editedItem.description).isEqualTo("new description")
        assertThat(editedItem.itemCategory).isEqualTo(ItemCategory.FILM)
        assertThat(editedItem.preferredEnvironment).isEqualTo(Environment.FREEZE)
        assertThat(editedItem.packaging).isEqualTo(Packaging.BOX)
        assertThat(editedItem.callbackUrl).isEqualTo("https://callback-wls.no/v2/item")
    }
}
