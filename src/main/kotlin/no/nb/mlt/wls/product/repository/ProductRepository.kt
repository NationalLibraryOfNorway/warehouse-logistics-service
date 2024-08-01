package no.nb.mlt.wls.product.repository

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.model.Product
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductRepository : MongoRepository<Product, String> {
    fun findByHostNameAndHostId(
        hostName: HostName,
        hostId: String
    ): Product?
}
