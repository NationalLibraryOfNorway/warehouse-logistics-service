package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.storageMessages.StorageMessage

interface StorageMessageRepository {
    suspend fun save(storageMessage: StorageMessage): StorageMessage

    suspend fun getAll(): List<StorageMessage>

    suspend fun getUnprocessedSortedByCreatedTime(): List<StorageMessage>

    suspend fun markAsProcessed(storageMessage: StorageMessage): StorageMessage
}
