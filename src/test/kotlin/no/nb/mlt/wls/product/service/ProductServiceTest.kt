package no.nb.mlt.wls.product.service

import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nb.mlt.wls.core.data.Environment.NONE
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.repository.ProductRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.server.ServerWebInputException
import org.assertj.core.api.Assertions.assertThatExceptionOfType as assertException

@TestInstance(PER_CLASS)
@ExtendWith(MockKExtension::class)
class ProductServiceTest {
    @MockK
    private lateinit var db: ProductRepository

    @MockK
    private lateinit var synqProductService: SynqProductService

    @InjectMockKs
    private lateinit var productService: ProductService

    @Test
    fun `save with payload mi`() {
        assertException(ServerWebInputException::class.java).isThrownBy {
            productService.save(testProductPayload.copy(hostId = ""))
        }.withMessageContaining("hostId is required")

        assertException(ServerWebInputException::class.java).isThrownBy {
            productService.save(testProductPayload.copy(hostId = "      "))
        }.withMessageContaining("hostId is required")

        assertException(ServerWebInputException::class.java).isThrownBy {
            productService.save(testProductPayload.copy(hostId = "\t\n"))
        }.withMessageContaining("hostId is required")
    }

// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// Test Help //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    // Will be used in most tests
    private val testProductPayload =
        ApiProductPayload(
            hostId = "mlt-420",
            hostName = HostName.AXIELL,
            description = "Ringenes Herre samling",
            productCategory = "BOOK",
            preferredEnvironment = NONE,
            packaging = Packaging.BOX,
            owner = Owner.NB,
            location = "SYNQ_WAREHOUSE",
            quantity = 1.0
        )
}
