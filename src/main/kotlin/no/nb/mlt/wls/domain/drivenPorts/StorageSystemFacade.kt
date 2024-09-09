package no.nb.mlt.wls.domain.drivenPorts

import no.nb.mlt.wls.domain.Item

interface StorageSystemFacade {
    fun createItem(item: Item)
}
