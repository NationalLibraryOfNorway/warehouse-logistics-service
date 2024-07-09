package no.nb.mlt.wls.product.payloads

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
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
    val environment: Environment,
    @Schema(example = "NB")
    val owner: Owner
)

fun ApiProductPayload.toProduct() =
    Product(
        hostName = hostName,
        hostId = hostId,
        category = category,
        description = description,
        packaging = packaging,
        location = location,
        quantity = quantity,
        environment = environment,
        owner = owner
    )

fun Product.toApiPayload() =
    ApiProductPayload(
        hostName = hostName,
        hostId = hostId,
        category = category,
        description = description,
        packaging = packaging,
        location = location,
        quantity = quantity,
        environment = environment,
        owner = owner
    )

fun ApiProductPayload.toSynqPayload(): SynqProductPayload {
    return SynqProductPayload(
        productId = this.hostId,
        owner = this.owner.toSynqOwner(),
        barcode = SynqProductPayload.Barcode(this.hostId),
        productCategory = category,
        productUom = SynqProductPayload.ProductUom(packaging.toSynqPackaging()),
        confidential = false,
        hostName = this.hostName.name
    )
}
