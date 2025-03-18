package no.nb.mlt.wls.domain.model.storageMessages

sealed interface StorageMessage {
    val id: String
    val body: Any
}
