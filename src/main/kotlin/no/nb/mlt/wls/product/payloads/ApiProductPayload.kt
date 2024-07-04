package no.nb.mlt.wls.product.payloads

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.core.data.PreferredEnvironment
import no.nb.mlt.wls.product.model.Product

data class ApiProductPayload(
    @Schema(example = "AXIELL")
    val hostName: HostName,
    @Schema(example = "product-12345")
    val hostId: String,
    @Schema(example = "BOOK")
    val category: String,
    @Schema(example = "Tyv etter loven")
    val description: String,
    @Schema(example = "NONE")
    val packaging: Packaging,
    @Schema(example = "SYNQ_WAREHOUSE")
    val location: String,
    @Schema(example = "1.0")
    val quantity: Float,
    @Schema(example = "NONE")
    val preferredEnvironment: PreferredEnvironment,
    @Schema(example = "NB")
    val owner: Owner
)

fun ApiProductPayload.toProduct() = Product(
    hostName = hostName,
    hostId = hostId,
    category = category,
    description = description,
    packaging = packaging,
    location = location,
    quantity = quantity,
    preferredEnvironment = preferredEnvironment,
    owner = owner
)

fun Product.toPayload() = ApiProductPayload(
    hostName = hostName,
    hostId = hostId,
    category = category,
    description = description,
    packaging = packaging,
    location = location,
    quantity = quantity,
    preferredEnvironment = preferredEnvironment,
    owner = owner
)
