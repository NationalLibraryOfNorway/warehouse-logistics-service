package no.nb.mlt.wls.database

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.extensions.spring.SpringTestExtension
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.model.Product
import no.nb.mlt.wls.product.repository.ProductRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@EnableTestcontainers
@ApplyExtension(SpringTestExtension::class)
@SpringBootTest
class MongoDBTests(
    @Autowired val service: ProductRepository
) : AnnotationSpec() {
    override fun extensions() = listOf(SpringExtension)

    @BeforeAll
    fun setup() {
        ProductSeeds(service).populateDb()
    }

    @Test
    fun contextLoaded() {
        assert(true)
    }

    @Test
    fun findExistingProduct() {
        val testProduct = service.findByHostNameAndHostId(HostName.AXIELL, "product-12346")
        assert(testProduct != null)
    }

    @Test
    fun insertProduct() {
        val testProduct =
            Product(
                hostName = HostName.AXIELL,
                hostId = "mlt-2048",
                productCategory = "BOOK",
                description = "Ringenes Herre samling",
                packaging = Packaging.BOX,
                location = "SYNQ_WAREHOUSE",
                quantity = 1.0,
                preferredEnvironment = Environment.NONE,
                owner = Owner.NB
            )
        val result = service.save(testProduct)
        assert(result.hostId.equals("mlt-2048"))
        assert(result.packaging.equals(Packaging.BOX))
    }
}
