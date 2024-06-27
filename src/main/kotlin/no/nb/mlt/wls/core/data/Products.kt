package no.nb.mlt.wls.core.data

import io.wispforest.endec.Endec
import io.wispforest.endec.impl.StructEndecBuilder

@JvmRecord
data class Products(
    val hostName: HostName,
    val hostId: String,
    val product: InnerProduct,
    val confidential: Boolean,
    val preferredEnvironment: PreferredEnvironment,
    val owner: Owner
) {
    companion object {
        val ENDEC: Endec<Products> =
            StructEndecBuilder.of(
                Endec.forEnum(HostName::class.java).fieldOf("hostName", Products::hostName),
                Endec.STRING.fieldOf("hostId", Products::hostId),
                InnerProduct.ENDEC.fieldOf("product", Products::product),
                Endec.BOOLEAN.fieldOf("confidential", Products::confidential),
                Endec.forEnum(PreferredEnvironment::class.java)
                    .fieldOf("preferredEnvironment", Products::preferredEnvironment),
                Endec.forEnum(Owner::class.java).fieldOf("owner", Products::owner)
            ) { hostName: HostName, hostId: String, product: InnerProduct, confidential: Boolean,
                preferredEnvironment: PreferredEnvironment, owner: Owner ->
                Products(
                    hostName,
                    hostId,
                    product,
                    confidential,
                    preferredEnvironment,
                    owner
                )
            }
                .validate { product1: Products? ->
                    // TODO - Validation
                }
    }
}
