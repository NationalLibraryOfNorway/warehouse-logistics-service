package no.nb.mlt.wls.infrastructure.repositories.mail

import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import no.nb.mlt.wls.domain.model.HostEmail
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.outbound.EmailRepository
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Component
class MongoEmailRepositoryAdapter(
    private val repository: MongoEmailRepository
) : EmailRepository {
    override suspend fun createHostEmail(
        hostName: HostName,
        email: String
    ) {
        repository.save(HostEmail(hostName, email)).awaitSingle()
    }

    override suspend fun getHostEmail(hostName: HostName): HostEmail? {
        return repository.findByHost(hostName).awaitFirstOrNull()
    }

    override suspend fun getHostEmails(hosts: List<HostName>): List<HostEmail> {
        return repository.findAll().collectList().filter { (h) -> hosts.contains(h.host) }.awaitFirst()
    }
}

@Repository
interface MongoEmailRepository : ReactiveMongoRepository<HostEmail, String> {
    fun findByHost(hostName: HostName): Mono<HostEmail>
}
