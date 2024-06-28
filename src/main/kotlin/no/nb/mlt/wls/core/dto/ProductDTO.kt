package no.nb.mlt.wls.core.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.PreferredEnvironment

@JvmRecord
data class ProductDTO(
    @field:Schema(example = "test") @param:Schema(example = "test") val hostName: HostName,
    val hostId: String,
    val product: InnerProductDTO,
    val confidential: Boolean?,
    val preferredEnvironment: PreferredEnvironment,
    val owner: Owner
)
