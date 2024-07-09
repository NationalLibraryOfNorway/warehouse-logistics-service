package no.nb.mlt.wls.product.model

import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "products")
data class Product(
    val hostName: HostName,
    val hostId: String,
    val category: String,
    val description: String,
    val packaging: Packaging,
    val location: String,
    val quantity: Float,
    val environment: Environment,
    val owner: Owner
)
