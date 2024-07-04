package no.nb.mlt.wls.product.repository

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.model.ProductModel
import org.springframework.data.mongodb.repository.MongoRepository

interface ProductRepository : MongoRepository<ProductModel, String> {
    fun findByHostName(hostName: HostName): List<ProductModel>
}
