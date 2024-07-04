package no.nb.mlt.wls.product.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.core.data.PreferredEnvironment

@JvmRecord
data class ProductDTO(
    @Schema(example = "ALMA") val hostName: HostName,
    @Schema(example = "product-12345") val hostId: String,
    @Schema(example = "BOOK") val category: String,
    @Schema(example = "Tyv etter loven") val description: String,
    @Schema(example = "OBJ") val packaging: PackagingDTO,
    @Schema(example = "SYNQ_WAREHOUSE") val location: String,
    @Schema(example = "1.0") val quantity: Float,
    @Schema(example = "NONE") val preferredEnvironment: PreferredEnvironment,
    @Schema(example = "NB") val owner: Owner
)
