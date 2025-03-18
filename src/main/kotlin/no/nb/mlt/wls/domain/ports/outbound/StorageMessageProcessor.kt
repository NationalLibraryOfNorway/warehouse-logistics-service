package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.storageMessages.StorageMessage

fun interface StorageMessageProcessor {
    suspend fun handleEvent(event: StorageMessage)
}
