package no.nb.mlt.wls.domain.model

enum class HostName {
    ALMA,
    ASTA,
    MAVIS,
    AXIELL,
    NONE;

    companion object {
        fun fromString(string: String): HostName {
            return HostName.valueOf(string.uppercase())
        }
    }
}
