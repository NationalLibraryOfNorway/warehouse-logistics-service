package no.nb.mlt.wls.product.service

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.model.ProductModel
import no.nb.mlt.wls.product.repository.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductService(val db: ProductRepository) {
    fun save(products: ProductModel) {
        db.save(products)
    }

    fun getByHostName(hostName: HostName): List<ProductModel> {
        return db.findByHostName(hostName)
    }
}
