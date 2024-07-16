package no.nb.mlt.wls.database

import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.model.Product
import no.nb.mlt.wls.product.repository.ProductRepository
import org.springframework.beans.factory.annotation.Autowired

class ProductSeeds(
    @Autowired private val repository: ProductRepository
) {
    fun populateDb() {
        repository.save(
            Product(
                hostName = HostName.AXIELL,
                hostId = "product-12346",
                productCategory = "BOOK",
                description = "Tyv etter loven",
                packaging = Packaging.NONE,
                location = "SYNQ_WAREHOUSE",
                quantity = 1.0,
                preferredEnvironment = Environment.NONE,
                owner = Owner.NB
            )
        )
    }
}
