package no.nb.mlt.wls.product.service

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.model.ProductModel
import no.nb.mlt.wls.product.repository.ProductsRepository
import org.springframework.stereotype.Service

@Service
class ProductsService(val db: ProductsRepository) {
    fun save(products: ProductModel) {
        db.save(products)
    }

    fun getByHostName(hostName: HostName): List<ProductModel> {
        return db.findByHostName(hostName)
    }
}
