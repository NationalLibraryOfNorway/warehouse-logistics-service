package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import java.time.Instant

interface InventoryNotifier {
    @Throws(UnableToNotifyException::class)
    fun itemChanged(
        item: Item,
        eventTimestamp: Instant,
        messageId: String
    )

    @Throws(UnableToNotifyException::class)
    fun orderChanged(
        order: Order,
        eventTimestamp: Instant,
        messageId: String
    )
}

class UnableToNotifyException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
