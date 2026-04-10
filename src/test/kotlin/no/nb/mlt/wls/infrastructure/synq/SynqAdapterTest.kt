package no.nb.mlt.wls.infrastructure.synq

import kotlinx.coroutines.runBlocking
import no.nb.mlt.wls.infrastructure.config.TimeoutProperties
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class SynqAdapterTest {

    private val timeoutProperties = TimeoutProperties(5, 5, 5)

    private val synqAdapter = SynqAdapter(
        webClient = WebClient.builder().build(),
        baseUrl = "http://localhost:8080",
        timeoutProperties = timeoutProperties
    )

    @Test
    fun `editItem should not throw IllegalArgumentException when productId contains spaces`() {
        val product = SynqProductPayload(
            productId = "FT  42220039",
            owner = SynqOwner.NB,
            barcode = SynqProductPayload.Barcode("FT  42220039"),
            description = "Test description",
            productCategory = "Film",
            productUom = SynqProductPayload.ProductUom(SynqProductPayload.SynqPackaging.OBJ),
            confidential = false,
            hostName = "Axiell"
        )

        runBlocking {
            try {
                synqAdapter.editItem(product)
            } catch (e: Exception) {
                if (e is IllegalArgumentException && e.message?.contains("Illegal character in path") == true) {
                    throw e
                }
            }
        }
    }

    @Test
    fun `deleteOrder should not throw IllegalArgumentException when synqOrderId contains spaces`() {
        runBlocking {
            try {
                synqAdapter.deleteOrder(SynqOwner.NB, "ORDER  123")
            } catch (e: Exception) {
                if (e is IllegalArgumentException && e.message?.contains("Illegal character in path") == true) {
                    throw e
                }
            }
        }
    }
}
