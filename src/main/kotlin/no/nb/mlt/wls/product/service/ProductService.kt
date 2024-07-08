package no.nb.mlt.wls.product.service

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.model.Product
import no.nb.mlt.wls.product.repository.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductService(val db: ProductRepository) {

    fun exists(product: Product): Boolean {
        return db.existsByHostId(product.hostId);
    }

    fun save(products: Product) {
        db.save(products)
    }

    fun getByHostName(hostName: HostName): List<Product> {
        return db.findByHostName(hostName)
    }
}
