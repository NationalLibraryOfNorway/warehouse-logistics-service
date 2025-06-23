package no.nb.mlt.wls.domain.model

enum class ItemCategory {
    PAPER,
    DISC,
    FILM,
    EQUIPMENT,
    BULK_ITEMS,
    MAGNETIC_TAPE,
    PHOTO,
    UNKNOWN;

    companion object {
        fun fromString(value: String): ItemCategory = valueOf(value.uppercase())
    }
}
