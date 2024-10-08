package no.nb.mlt.wls.domain.model

data class Item(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: String,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val owner: Owner,
    val callbackUrl: String?,
    val location: String?,
    val quantity: Double?
)
