package no.nb.mlt.wls.core.data

import io.wispforest.endec.Endec
import io.wispforest.endec.impl.StructEndecBuilder

@JvmRecord
data class InnerProduct(val category: String, val description: String, val packaging: Packaging, val id: String) {
    companion object {
        @JvmField
        val ENDEC: Endec<InnerProduct> =
            StructEndecBuilder.of(
                Endec.STRING.fieldOf("category", InnerProduct::category),
                Endec.STRING.fieldOf("description", InnerProduct::description),
                Endec.forEnum(Packaging::class.java).fieldOf("packaging", InnerProduct::packaging),
                Endec.STRING.fieldOf("id", InnerProduct::id)
            ) { category: String, description: String, packaging: Packaging, id: String ->
                InnerProduct(
                    category,
                    description,
                    packaging,
                    id
                )
            }
                .validate { product: InnerProduct? -> // TODO - Validation
                }
    }
}
