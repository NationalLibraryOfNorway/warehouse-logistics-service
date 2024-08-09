package no.nb.mlt.wls.product.repository

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.model.Product
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface ProductRepository : ReactiveMongoRepository<Product, String> {
    fun findByHostNameAndHostId(
        hostName: HostName,
        hostId: String
    ): Mono<Product>
}
