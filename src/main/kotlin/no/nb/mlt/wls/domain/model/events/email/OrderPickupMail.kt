package no.nb.mlt.wls.domain.model.events.email

import no.nb.mlt.wls.domain.model.OrderEmail
import java.util.UUID

data class OrderPickupMail(
    val orderEmail: OrderEmail,
    override val id: String = UUID.randomUUID().toString()
) : EmailEvent {
    override val body: Any
        get() = (orderEmail)
}
