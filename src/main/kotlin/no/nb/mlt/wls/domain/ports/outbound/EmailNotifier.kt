package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Order

interface EmailNotifier {
    suspend fun orderCreated(order: Order)

    suspend fun orderUpdated(order: Order)
}
