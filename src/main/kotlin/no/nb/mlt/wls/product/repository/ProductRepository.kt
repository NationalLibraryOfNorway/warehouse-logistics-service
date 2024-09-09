package no.nb.mlt.wls.product.repository

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.domain.Item
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface ProductRepository : ReactiveMongoRepository<Item, String> {
    fun findByHostNameAndHostId(
        hostName: HostName,
        hostId: String
    ): Mono<Item>
}
