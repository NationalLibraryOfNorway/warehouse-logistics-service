package no.nb.mlt.wls

import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging

fun createTestItem(
    hostName: HostName = HostName.AXIELL,
    hostId: String = "mlt-12345",
    description: String = "Tyven, tyven skal du hete",
    itemCategory: ItemCategory = ItemCategory.PAPER,
    preferredEnvironment: Environment = Environment.NONE,
    packaging: Packaging = Packaging.NONE,
    callbackUrl: String? = "https://callback-wls.no/item",
    location: String = "UNKNOWN",
    quantity: Int = 0
) = Item(hostId, hostName, description, itemCategory, preferredEnvironment, packaging, callbackUrl, location, quantity)
