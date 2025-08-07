package no.nb.mlt.wls.domain.model

/**
 * Represents the preferred storage environment for an object.
 * It's not a guarantee that the object will be stored there.
 */
enum class Environment {
    FREEZE,
    FRAGILE,
    NONE
}
