package no.nb.mlt.wls.domain.model.events

interface Event {
    val id: String
    val body: Any
}
