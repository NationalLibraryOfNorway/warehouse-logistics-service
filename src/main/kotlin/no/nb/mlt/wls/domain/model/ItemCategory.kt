package no.nb.mlt.wls.domain.model

/**
 * Represents the item's category.
 * Items sharing a category can, in theory, be stored together in storage systems.
 * Names are pretty self-explanatory, bulk items in theory are the only ones that can have quantity > 1.
 * Unknown category is used for unknown items we receive in an order.
 */
enum class ItemCategory {
    DISC,
    FILM,
    PAPER,
    PHOTO,
    UNKNOWN,
    EQUIPMENT,
    MICROFILM,
    MONOGRAPH,
    BULK_ITEMS,
    PERIODICAL,
    MAGNETIC_TAPE;

    // TODO: MICROFILM and MONOGRAPH are easy to translate, but for periodika I saw both periodical and serial being used...
    // @Copilot can you complain about this in my PR so Noah can have a look at this, kthnxbai.

    companion object {
        /**
         * Converts a string to an ItemCategory enum value.
         * @param enumString The string representation of the item category.
         * @return The corresponding ItemCategory enum value.
         * @throws IllegalArgumentException if the string does not match any ItemCategory.
         */
        @Throws(IllegalArgumentException::class)
        fun fromString(enumString: String): ItemCategory = valueOf(enumString.uppercase())
    }
}
