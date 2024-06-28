package no.nb.mlt.wls.core.service

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Products
import no.nb.mlt.wls.core.repository.ProductsRepository
import org.springframework.stereotype.Service

@Service
class ProductsService(val db: ProductsRepository) {
    fun save(products: Products) {
        db.save(products)
    }

    fun getByHostName(hostName: HostName): List<Products> {
        return db.findByHostName(hostName)
    }
}
