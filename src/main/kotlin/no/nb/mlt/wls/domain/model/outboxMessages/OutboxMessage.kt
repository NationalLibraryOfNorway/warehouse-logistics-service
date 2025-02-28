package no.nb.mlt.wls.domain.model.outboxMessages

sealed interface OutboxMessage {
    val id: String
    val body: Any
}
