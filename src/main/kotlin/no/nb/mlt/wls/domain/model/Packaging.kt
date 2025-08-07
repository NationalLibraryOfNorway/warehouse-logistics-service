package no.nb.mlt.wls.domain.model

/**
 * Represents the packaging type of item in the storage system.
 *
 * The packaging information is used to determine the type of container.
 * An Archival Box (ABOX) should only be used for items belonging to ASTA.
 */
enum class Packaging {
    BOX,
    ABOX,
    NONE,
    UNKNOWN
}
