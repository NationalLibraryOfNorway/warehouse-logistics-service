package no.nb.mlt.wls.product.repository

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.model.Product
import org.springframework.data.mongodb.repository.MongoRepository

interface ProductRepository : MongoRepository<Product, String> {
    fun findByHostNameAndHostId(hostName: HostName, id: String): Product?

    fun existsByHostId(id: String): Boolean
}
