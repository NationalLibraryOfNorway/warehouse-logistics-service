package no.nb.mlt.wls.product.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.core.data.Environment.NONE
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.payloads.toProduct
import no.nb.mlt.wls.product.repository.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ServerErrorException
import org.springframework.web.server.ServerWebInputException
import reactor.core.publisher.Mono
import java.net.URI
import java.util.concurrent.TimeoutException

@TestInstance(PER_CLASS)
@ExtendWith(MockKExtension::class)
class ProductServiceTest {
    @MockK
    private lateinit var db: ProductRepository

    @MockK
    private lateinit var synq: SynqProductService

    @InjectMockKs
    private lateinit var cut: ProductService // cut = class under test

    @Test
    fun `save called with payload missing hostId throws`() {
        assertExceptionThrownWithMessage(testProductPayload.copy(hostId = ""), "hostId is required", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(testProductPayload.copy(hostId = "\t\n"), "hostId is required", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(testProductPayload.copy(hostId = "      "), "hostId is required", ServerWebInputException::class.java)
    }

    @Test
    fun `save called with payload missing description throws`() {
        assertExceptionThrownWithMessage(testProductPayload.copy(description = ""), "description is required", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(
            testProductPayload.copy(description = "\t\n"),
            "description is required",
            ServerWebInputException::class.java
        )
        assertExceptionThrownWithMessage(
            testProductPayload.copy(description = "      "),
            "description is required",
            ServerWebInputException::class.java
        )
    }

    @Test
    fun `save called with payload missing productCategory throws`() {
        assertExceptionThrownWithMessage(testProductPayload.copy(productCategory = ""), "category is required", ServerWebInputException::class.java)
        assertExceptionThrownWithMessage(
            testProductPayload.copy(productCategory = "\t\n"),
            "category is required",
            ServerWebInputException::class.java
        )
        assertExceptionThrownWithMessage(
            testProductPayload.copy(productCategory = "      "),
            "category is required",
            ServerWebInputException::class.java
        )
    }

    @Test
    fun `save called with existing product, returns existing product`() =
        runTest {
            every {
                db.findByHostNameAndHostId(
                    testProductPayload.hostName,
                    testProductPayload.hostId
                )
            } returns Mono.just(testProductPayload.toProduct())

            val response = cut.save(testProductPayload)

            assertThat(response.body).isEqualTo(testProductPayload)
            assertThat(response.statusCode).isEqualTo(OK)
        }

    @Test
    fun `save called with product that SynQ says exists, returns without a product`() =
        runTest {
            every { db.findByHostNameAndHostId(testProductPayload.hostName, testProductPayload.hostId) } returns Mono.empty()
            coEvery { synq.createProduct(any()) } returns ResponseEntity.ok().build()

            val response = cut.save(testProductPayload)
            assertThat(response.body).isNull()
            assertThat(response.statusCode).isEqualTo(OK)
        }

    @Test
    fun `save when synq fails handles it gracefully`() {
        every { db.findByHostNameAndHostId(testProductPayload.hostName, testProductPayload.hostId) } returns Mono.empty()
        coEvery { synq.createProduct(any()) } throws
            ServerErrorException(
                "Failed to create product in SynQ, the storage system responded with error code: '420' and error text: 'Blaze it LMAO'",
                Exception("420 Blaze it")
            )

        assertExceptionThrownWithMessage(testProductPayload, "error code: '420' and error text: 'Blaze it LMAO'", ServerErrorException::class.java)
    }

    @Suppress("ReactiveStreamsUnusedPublisher")
    @Test
    fun `save when DB fails handles it gracefully`() {
        every { db.findByHostNameAndHostId(testProductPayload.hostName, testProductPayload.hostId) } returns Mono.error(TimeoutException())
        coEvery { synq.createProduct(any()) } returns ResponseEntity.created(URI.create("")).build()
        every { db.save(any()) } returns Mono.error(Exception("DB is down"))

        assertExceptionThrownWithMessage(testProductPayload, "Failed to save product in the database", ServerErrorException::class.java)
    }

    @Test
    fun `save with no errors returns created product`() =
        runTest {
            every { db.findByHostNameAndHostId(testProductPayload.hostName, testProductPayload.hostId) } returns Mono.empty()
            coEvery { synq.createProduct(any()) } returns ResponseEntity.created(URI.create("")).build()
            every { db.save(any()) } returns Mono.just(testProductPayload.toProduct())

            val response = cut.save(testProductPayload)
            val cleanedProduct = testProductPayload.copy(quantity = 0.0, location = null)

            assertThat(response.body).isEqualTo(cleanedProduct)
            assertThat(response.statusCode).isEqualTo(CREATED)
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

    private fun <T : Throwable> assertExceptionThrownWithMessage(
        payload: ApiProductPayload,
        message: String,
        exception: Class<T>
    ) = assertThatExceptionOfType(exception).isThrownBy {
        runBlocking { cut.save(payload) }
    }.withMessageContaining(message)
}
