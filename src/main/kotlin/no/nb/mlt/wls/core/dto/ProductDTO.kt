package no.nb.mlt.wls.core.dto

import io.swagger.v3.oas.annotations.media.Schema
import io.wispforest.endec.Endec
import io.wispforest.endec.impl.StructEndecBuilder
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.PreferredEnvironment

@JvmRecord
data class ProductDTO(
    @field:Schema(example = "test") @param:Schema(example = "test") val hostName: HostName,
    val hostId: String,
    val product: InnerProductDTO,
    val confidential: Boolean,
    val preferredEnvironment: PreferredEnvironment,
    val owner: Owner
) {
    companion object {
        val ENDEC: Endec<ProductDTO> =
            StructEndecBuilder.of(
                Endec.forEnum(HostName::class.java).fieldOf("hostName", ProductDTO::hostName),
                Endec.STRING.fieldOf("hostId", ProductDTO::hostId),
                InnerProductDTO.ENDEC.fieldOf("product", ProductDTO::product),
                Endec.BOOLEAN.fieldOf("confidential", ProductDTO::confidential),
                Endec.forEnum(PreferredEnvironment::class.java)
                    .fieldOf("preferredEnvironment", ProductDTO::preferredEnvironment),
                Endec.forEnum(Owner::class.java).fieldOf("owner", ProductDTO::owner)
            ) { hostName: HostName, hostId: String, product: InnerProductDTO, confidential: Boolean,
                preferredEnvironment: PreferredEnvironment, owner: Owner ->
                ProductDTO(
                    hostName,
                    hostId,
                    product,
                    confidential,
                    preferredEnvironment,
                    owner
                )
            }
    }
}
