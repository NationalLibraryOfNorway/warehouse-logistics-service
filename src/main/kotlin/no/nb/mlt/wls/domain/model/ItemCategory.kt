package no.nb.mlt.wls.domain.model

enum class ItemCategory {
    PAPER,
    DISC,
    FILM,
    EQUIPMENT,
    BULK_ITEMS,
    MAGNETIC_TAPE,
    PHOTO;

    companion object {
        fun fromString(value: String): ItemCategory {
            return valueOf(value.uppercase())
        }
    }
}
