package no.nb.mlt.wls.domain

data class Item(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val productCategory: String,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val owner: Owner,
    val location: String?,
    val quantity: Double?
)
