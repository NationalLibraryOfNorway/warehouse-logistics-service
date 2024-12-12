package no.nb.mlt.wls.infrastructure.repositories.item

import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "items")
data class MongoItem(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val owner: Owner,
    val callbackUrl: String?,
    val location: String?,
    val quantity: Int?
)

fun Item.toMongoItem() =
    MongoItem(
        this.hostId,
        this.hostName,
        this.description,
        this.itemCategory,
        this.preferredEnvironment,
        this.packaging,
        this.owner,
        this.callbackUrl,
        this.location,
        this.quantity
    )

fun MongoItem.toItem() =
    Item(
        this.hostId,
        this.hostName,
        this.description,
        this.itemCategory,
        this.preferredEnvironment,
        this.packaging,
        this.owner,
        this.callbackUrl,
        this.location,
        this.quantity
    )
