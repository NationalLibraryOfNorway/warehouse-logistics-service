package no.nb.mlt.wls.product.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.core.data.PreferredEnvironment

@JvmRecord
data class ProductDTO(
    @Schema(example = "ALMA") val hostName: HostName,
    val hostId: String,
    val category: String,
    val description: String,
    val packaging: Packaging,
    val location: String,
    val quantity: Int,
    val preferredEnvironment: PreferredEnvironment,
    val owner: Owner
)
