package no.nb.mlt.wls.product.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.junit5.MockKExtension
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.core.data.Environment.NONE
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.payloads.toProduct
import no.nb.mlt.wls.product.repository.ProductRepository
import no.nb.mlt.wls.product.service.ProductService
import no.nb.mlt.wls.product.service.SynqProductService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ServerErrorException
import java.net.URI

@EnableTestcontainers
@TestInstance(PER_CLASS)
@AutoConfigureWebTestClient
@ExtendWith(MockKExtension::class)
@EnableMongoRepositories("no.nb.mlt.wls")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ProductControllerTest(
    @Autowired val repository: ProductRepository
) {
    @MockkBean
    private lateinit var synqProductService: SynqProductService

    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        webTestClient =
            WebTestClient
                .bindToController(ProductController(ProductService(repository, synqProductService)))
                .configureClient()
                .baseUrl("/v1/product")
                .build()

        populateDb()
    }

    @Test
    @WithMockUser
    fun `createProduct with valid payload creates product`() {
        every {
            synqProductService.createProduct(any())
        } returns ResponseEntity.created(URI.create("")).build()

        webTestClient
            .mutateWith(csrf())
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testProductPayload)
            .exchange()
            .expectStatus().isCreated

        val product = repository.findByHostNameAndHostId(testProductPayload.hostName, testProductPayload.hostId)

        assertThat(product)
            .isNotNull
            .extracting("description", "location", "quantity")
            .containsExactly(testProductPayload.description, null, 0.0)
    }

    @Test
    @WithMockUser
    fun `createProduct with duplicate payload returns OK`() {
        webTestClient
            .mutateWith(csrf())
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(duplicateProductPayload)
            .exchange()
            .expectStatus().isOk
            .expectBody<ApiProductPayload>()
            .consumeWith { response ->
                assertThat(response.responseBody?.hostId).isEqualTo(duplicateProductPayload.hostId)
                assertThat(response.responseBody?.hostName).isEqualTo(duplicateProductPayload.hostName)
                assertThat(response.responseBody?.description).isEqualTo(duplicateProductPayload.description)
            }
    }

    @Test
    @WithMockUser
    fun `createProduct payload with different data but same ID returns DB entry`() {
        webTestClient
            .mutateWith(csrf())
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(
                duplicateProductPayload.copy(description = "Test")
            )
            .exchange()
            .expectStatus().isOk
            .expectBody<ApiProductPayload>()
            .consumeWith { response ->
                // This value is different in payload, response value should be the same as in DB
                assertThat(response.responseBody?.description).isEqualTo(duplicateProductPayload.description)
            }
    }

    @Test
    @WithMockUser
    fun `createProduct where SynQ says it's a duplicate returns OK`() {
        every {
            synqProductService.createProduct(any())
        } returns ResponseEntity.ok().build()

        webTestClient
            .mutateWith(csrf())
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testProductPayload)
            .exchange()
            .expectStatus().isOk
            .expectBody().isEmpty
    }

    @Test
    @WithMockUser
    fun `createProduct handles SynQ error`() {
        every {
            synqProductService.createProduct(any())
        }.throws(
            ServerErrorException(
                "Failed to create product in SynQ, the storage system responded with " +
                    "error code: '1002' and error text: 'Unknown product category TEST.'",
                HttpClientErrorException(HttpStatus.NOT_FOUND, "Not found")
            )
        )

        webTestClient
            .mutateWith(csrf())
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testProductPayload)
            .exchange()
            .expectStatus().is5xxServerError
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

    // Will exist in the database
    private val duplicateProductPayload =
        ApiProductPayload(
            hostId = "product-12346",
            hostName = HostName.AXIELL,
            description = "Tyv etter loven",
            productCategory = "BOOK",
            preferredEnvironment = NONE,
            packaging = Packaging.NONE,
            owner = Owner.NB,
            location = "SYNQ_WAREHOUSE",
            quantity = 1.0
        )

    fun populateDb() {
        // Make sure we start with clean DB instance for each test
        repository.deleteAll()
        repository.save(duplicateProductPayload.toProduct())
    }
}
