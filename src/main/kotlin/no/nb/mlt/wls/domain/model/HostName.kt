package no.nb.mlt.wls.domain.model

enum class HostName {
    ALMA,
    ASTA,
    MAVIS,
    AXIELL,
    TEMP_STORAGE,
    NONE;

    companion object {
        fun fromString(string: String): HostName {
            val potentialHost = string.uppercase()
            if (potentialHost == "MELLOMLAGER") return TEMP_STORAGE
            return HostName.valueOf(potentialHost)
        }
    }
}
