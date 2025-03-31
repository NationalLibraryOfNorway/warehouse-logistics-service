package no.nb.mlt.wls.infrastructure

import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Component
class SpringTransactionAdapter(
    private val transactionalOperator: TransactionalOperator
) : TransactionPort {
    override suspend fun <T> executeInTransaction(action: suspend () -> T): T? =
        transactionalOperator.executeAndAwait {
            action()
        }
}
