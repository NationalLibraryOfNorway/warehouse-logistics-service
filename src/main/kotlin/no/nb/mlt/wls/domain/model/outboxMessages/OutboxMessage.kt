package no.nb.mlt.wls.domain.model.outboxMessages

sealed interface OutboxMessage {
    val body: Any
}
