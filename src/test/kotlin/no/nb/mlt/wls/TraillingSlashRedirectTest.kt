package no.nb.mlt.wls

import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.application.hostapi.item.toApiPayload
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.toMongoItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.web.reactive.server.WebTestClient

@EnableTestcontainers
@TestInstance(PER_CLASS)
@AutoConfigureWebTestClient
@ExtendWith(MockKExtension::class)
@EnableMongoRepositories("no.nb.mlt.wls")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class TraillingSlashRedirectTest(
    @Autowired val applicationContext: ApplicationContext,
    @Autowired val repository: ItemMongoRepository
) {
    private lateinit var webTestClient: WebTestClient

    val client: String = HostName.AXIELL.name

    @BeforeEach
    fun setUp() {
        webTestClient =
            WebTestClient
                .bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .configureClient()
                .baseUrl("/v1/item/")
                .build()
    }

    @Test
    fun `path with a trailling slash redirects to proper endpoint`() =
        runTest {
            repository.save(itemPayload.toItem().toMongoItem()).awaitSingle()

            webTestClient
                .mutateWith(csrf())
                .mutateWith(
                    mockJwt().jwt { it.subject(client) }.authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority("ROLE_axiell"))
                ).post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(itemPayload)
                .exchange()
                .expectStatus()
                .isOk
        }

    private val itemPayload = createTestItem(hostId = "traillingSlashTestItem").toApiPayload()
}
