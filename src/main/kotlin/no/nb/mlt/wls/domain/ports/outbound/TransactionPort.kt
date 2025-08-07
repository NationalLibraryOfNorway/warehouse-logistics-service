package no.nb.mlt.wls.domain.ports.outbound

/**
 * Represents a port for executing actions within a transactional context.
 *
 * Implementations of this interface provide mechanisms to ensure the execution of
 * a given action within a transactional boundary, supporting rollback in case of failure.
 */
interface TransactionPort {
    suspend fun <T> executeInTransaction(action: suspend () -> T): T?
}
