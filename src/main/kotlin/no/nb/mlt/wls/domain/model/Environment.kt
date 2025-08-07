package no.nb.mlt.wls.domain.model

/**
 * Represents the preferred storage environment for an object.
 * It's not a guarantee that the object will be stored there.
 */
enum class Environment {
    /**
     * The object should be stored in a freezer.
     */
    FREEZE,
    /**
     * The object should be stored in an environment suited for fragile materials.
     */
    FRAGILE,
    /**
     * No preference for the storage environment.
     */
    NONE
}
