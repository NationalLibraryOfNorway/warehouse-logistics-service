package no.nb.mlt.wls.core.dto

import io.wispforest.endec.Endec
import io.wispforest.endec.impl.StructEndecBuilder
import no.nb.mlt.wls.core.data.Packaging

@JvmRecord
data class InnerProductDTO(val category: String, val description: String, val packaging: Packaging, val id: String) {
    companion object {
        @JvmField
        val ENDEC: Endec<InnerProductDTO> =
            StructEndecBuilder.of(
                Endec.STRING.fieldOf("category", InnerProductDTO::category),
                Endec.STRING.fieldOf("description", InnerProductDTO::description),
                Endec.forEnum(Packaging::class.java).fieldOf("packaging", InnerProductDTO::packaging),
                Endec.STRING.fieldOf("id", InnerProductDTO::id)
            ) { category: String, description: String, packaging: Packaging, id: String ->
                InnerProductDTO(
                    category,
                    description,
                    packaging,
                    id
                )
            }
    }
}
