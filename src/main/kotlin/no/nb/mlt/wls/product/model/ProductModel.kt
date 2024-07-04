package no.nb.mlt.wls.product.model

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.core.data.PreferredEnvironment

@JvmRecord
data class ProductModel(
    val hostName: HostName,
    val hostId: String,
    val category: String,
    val description: String,
    val packaging: Packaging,
    val location: String,
    val quantity: Float,
    val preferredEnvironment: PreferredEnvironment,
    val owner: Owner
)
