package no.nb.mlt.wls.domain

import io.mockk.mockk
import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import org.junit.jupiter.api.BeforeEach

class EmailServiceTest {
    private val emailEventRepository = mockk<EventRepository<EmailEvent>>()
    private val emailEventProcessor = mockk<EventProcessor<EmailEvent>>()
    private val transactionPort = mockk<TransactionPort>()
    private val transactionPortExecutor =
        object : TransactionPort {
            override suspend fun <T> executeInTransaction(action: suspend () -> T): T = action()
        }

    @BeforeEach
    fun beforeEach() {

    }

    // TODO - Tests
}
