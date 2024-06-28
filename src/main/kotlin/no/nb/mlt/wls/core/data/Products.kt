package no.nb.mlt.wls.core.data

@JvmRecord
data class Products(
    val hostName: HostName,
    val hostId: String,
    val product: InnerProduct,
    val confidential: Boolean,
    val preferredEnvironment: PreferredEnvironment,
    val owner: Owner
)
