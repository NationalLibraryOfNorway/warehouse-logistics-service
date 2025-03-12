package no.nb.mlt.wls.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

private val logger = KotlinLogging.logger {}

@Component
class SpringTransactionAdapter(
    private val transactionalOperator: TransactionalOperator
) : TransactionPort {
    override suspend fun <T> executeInTransaction(action: suspend () -> T): T? {
        return transactionalOperator.executeAndAwait {
            action()
        }
    }
}
