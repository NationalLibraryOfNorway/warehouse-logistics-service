package no.nb.mlt.wls.core.repository

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Products
import org.springframework.data.mongodb.repository.MongoRepository

interface ProductsRepository : MongoRepository<Products, String> {
    fun findByHostName(hostName: HostName): List<Products>
}
