package no.nb.mlt.wls.domain.model

/**
 * Represents which host item or order belongs to.
 * This enum is used to identify different hosts that can interact with the storage system.
 * It includes a method to convert a string representation of a hostname into the corresponding enum value.
 */
enum class HostName {
    ALMA,
    ASTA,
    MAVIS,
    AXIELL,
    TEMP_STORAGE,
    UNKNOWN;

    companion object {
        /**
         * Converts a string to a HostName enum value.
         * If the string is "MELLOMLAGER", it returns TEMP_STORAGE.
         * Otherwise, it converts the string to uppercase and returns the corresponding HostName.
         *
         * @param enumString The string representation of the host name.
         * @throws IllegalArgumentException if the string does not match any HostName.
         *         This includes cases where the string is not a valid enum constant.
         * @return The corresponding HostName enum value.
         */
        @Throws(IllegalArgumentException::class)
        fun fromString(enumString: String): HostName {
            val potentialHost = enumString.uppercase()
            if (potentialHost == "MELLOMLAGER") return TEMP_STORAGE
            return HostName.valueOf(potentialHost)
        }
    }
}
