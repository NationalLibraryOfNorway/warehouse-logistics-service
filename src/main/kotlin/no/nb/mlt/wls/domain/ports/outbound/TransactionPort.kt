package no.nb.mlt.wls.domain.ports.outbound

interface TransactionPort {
    suspend fun <T> executeInTransaction(action: suspend () -> T): T?
}
