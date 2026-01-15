package no.nb.mlt.wls.infrastructure.repositories.item

import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "items")
@CompoundIndex(unique = true, def = "{'hostName':1,'hostId':1}")
data class MongoItem(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val callbackUrl: String?,
    val location: String,
    val quantity: Int,
    val associatedStorage: AssociatedStorage,
    val confidential: Boolean
)

fun Item.toMongoItem() =
    MongoItem(
        this.hostId,
        this.hostName,
        this.description,
        this.itemCategory,
        this.preferredEnvironment,
        this.packaging,
        this.callbackUrl,
        this.location,
        this.quantity,
        this.associatedStorage,
        this.confidential
    )

fun MongoItem.toItem() =
    Item(
        this.hostId,
        this.hostName,
        this.description,
        this.itemCategory,
        this.preferredEnvironment,
        this.packaging,
        this.callbackUrl,
        this.location,
        this.quantity,
        this.associatedStorage,
        this.confidential
    )
