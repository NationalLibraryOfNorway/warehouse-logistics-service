package no.nb.mlt.wls.application.restapi.product

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata

@Schema(
    description = "Payload for registering a product in Hermes WLS, and appropriate storage system for the product.",
    example = """
    {
      "hostId": "mlt-12345",
      "hostName": "AXIELL",
      "description": "Tyven, tyven skal du hete",
      "productCategory": "BOOK",
      "preferredEnvironment": "NONE",
      "packaging": "NONE",
      "owner": "NB"
    }
    """
)
data class ApiProductPayload(
    @Schema(
        description = "The product ID from the host system, usually a barcode or an equivalent ID.",
        example = "mlt-12345"
    )
    val hostId: String,
    @Schema(
        description =
            "Name of the host system which the product originates from. " +
                "Host system is usually the catalogue that the product is registered in.",
        examples = [ "AXIELL", "ALMA", "ASTA", "BIBLIOFIL" ]
    )
    val hostName: HostName,
    @Schema(
        description =
            "Description of the product for easy identification in the warehouse system. " +
                "Usually a product title/name, e.g. book title, film name, etc. or contents description.",
        examples = ["Tyven, tyven skal du hete", "Avisa Hemnes", "Kill Buljo"]
    )
    val description: String,
    @Schema(
        description = "What kind of product category the item belongs to, e.g. Books, Issues, Films, etc.",
        examples = ["BOOK", "ISSUE", "Arkivmateriale", "Film_Frys"]
    )
    val productCategory: String,
    @Schema(
        description = "What kind of environment the product should be stored in, e.g. NONE, FRYS, MUGG_CELLE, etc.",
        examples = ["NONE", "FRYS"]
    )
    val preferredEnvironment: Environment,
    @Schema(
        description =
            "Whether the product is a single object or a box/abox/crate of other items. " +
                "NONE is for single objects, BOX is for boxes, ABOX is for archival boxes, and CRATE is for crates.",
        examples = ["NONE", "BOX", "ABOX", "CRATE"]
    )
    val packaging: Packaging,
    @Schema(
        description = "Who owns the product. Usually the National Library of Norway (NB) or the National Archives of Norway (ARKIVVERKET).",
        examples = ["NB", "ARKIVVERKET"]
    )
    val owner: Owner,
    @Schema(
        description = "Where the product is located, e.g. SYNQ_WAREHOUSE, AUTOSTORE, KARDEX, etc.",
        examples = ["SYNQ_WAREHOUSE", "AUTOSTORE", "KARDEX"],
        accessMode = READ_ONLY,
        required = false
    )
    val location: String?,
    @Schema(
        description =
            "Quantity on hand of the product, this denotes if the product is in storage or not. " +
                "If the product is in storage then quantity is 1.0, if it's not in storage then quantity is 0.0.",
        examples = [ "0.0", "1.0"],
        accessMode = READ_ONLY,
        required = false
    )
    val quantity: Double?
)

fun ApiProductPayload.toProduct() =
    Item(
        hostId = hostId,
        hostName = hostName,
        description = description,
        productCategory = productCategory,
        preferredEnvironment = preferredEnvironment,
        packaging = packaging,
        owner = owner,
        location = location,
        quantity = quantity
    )

fun Item.toApiPayload() =
    ApiProductPayload(
        hostId = hostId,
        hostName = hostName,
        description = description,
        productCategory = productCategory,
        preferredEnvironment = preferredEnvironment,
        packaging = packaging,
        owner = owner,
        location = location,
        quantity = quantity
    )

fun ApiProductPayload.toItemMetadata(): ItemMetadata =
    ItemMetadata(
        hostId = hostId,
        hostName = hostName,
        description = description,
        productCategory = productCategory,
        preferredEnvironment = preferredEnvironment,
        packaging = packaging,
        owner = owner
    )
