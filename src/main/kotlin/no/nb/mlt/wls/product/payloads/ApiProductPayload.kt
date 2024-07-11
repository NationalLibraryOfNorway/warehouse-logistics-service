package no.nb.mlt.wls.product.payloads

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.model.Product

// TODO - Enforce style with validation. Should valid fields be returned in an error message, or be declared in the description?
data class ApiProductPayload(
    @Schema(description = "Name of the host where the product originated from", example = "Axiell")
    val hostName: HostName,
    @Schema(description = "The ID from the host", example = "mlt-12345")
    val hostId: String,
    @Schema(description = "What kind of item is being stored. Books, issues, etc.", example = "BOOK")
    val category: String,
    @Schema(
        description = "Describes what the item stored is. For objects this is usually the book name. For crates this is most often empty",
        example = "Tyv etter loven"
    )
    val description: String,
    @Schema(description = "Describes how the item is packaged, like whether it is in a box", example = "NONE")
    val packaging: Packaging,
    @Schema(description = "Location of where the object will be stored", example = "SYNQ_WAREHOUSE")
    val location: String,
    @Schema(description = "How many of this product is stored. Use whole numbers", example = "1.0")
    val quantity: Float,
    @Schema(description = "Describes how the product should be stored. Products which need special treatment, like ", example = "NONE")
    val preferredEnvironment: Environment,
    @Schema(description = "", example = "NB")
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
        environment = preferredEnvironment,
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
        preferredEnvironment = environment,
        owner = owner
    )
