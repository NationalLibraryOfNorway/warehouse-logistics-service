package no.nb.mlt.wls.product.model

import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "products")
data class Product(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val productCategory: String,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val owner: Owner,
    val location: String?,
    val quantity: Double?
)
