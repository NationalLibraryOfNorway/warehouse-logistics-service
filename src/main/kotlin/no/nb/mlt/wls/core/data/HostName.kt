package no.nb.mlt.wls.core.data

enum class HostName(private val hostName: String) {
    AXIELL("Axiell");

    override fun toString(): String {
        return hostName
    }
}
