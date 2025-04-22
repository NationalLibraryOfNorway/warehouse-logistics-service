package no.nb.mlt.wls.item.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.application.hostapi.item.ApiItemPayload
import no.nb.mlt.wls.application.hostapi.item.toApiPayload
import no.nb.mlt.wls.createTestItem
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION
import no.nb.mlt.wls.infrastructure.repositories.item.ItemMongoRepository
import no.nb.mlt.wls.infrastructure.repositories.item.toMongoItem
import no.nb.mlt.wls.infrastructure.synq.SynqStandardAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
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
import org.springframework.test.web.reactive.server.expectBody

@EnableTestcontainers
@TestInstance(PER_CLASS)
@AutoConfigureWebTestClient
@ExtendWith(MockKExtension::class)
@EnableMongoRepositories("no.nb.mlt.wls")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ItemControllerTest(
    @Autowired val applicationContext: ApplicationContext,
    @Autowired val repository: ItemMongoRepository
) {
    @MockkBean
    private lateinit var synqStandardAdapterMock: SynqStandardAdapter

    private lateinit var webTestClient: WebTestClient

    val clientRole: String = "ROLE_${HostName.AXIELL.name.lowercase()}"

    @BeforeEach
    fun setUp() {
        webTestClient =
            WebTestClient
                .bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .configureClient()
                .baseUrl("/hermes/v1/item")
                .build()

        populateDb()
    }

    @Test
    fun `createItem with valid payload creates item`() =
        runTest {
            coEvery {
                synqStandardAdapterMock.createItem(any())
            }.answers { }

            webTestClient
                .mutateWith(csrf())
                .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority(clientRole)))
                .post()
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(testItemPayload)
                .exchange()
                .expectStatus()
                .isCreated

            val item = repository.findByHostNameAndHostId(testItemPayload.hostName, testItemPayload.hostId).awaitSingle()

            assertThat(item)
                .isNotNull
                .extracting("description", "location", "quantity")
                .containsExactly(testItemPayload.description, UNKNOWN_LOCATION, 0)
        }

    @Test
    fun `createItem with duplicate payload returns OK`() {
        coEvery {
            synqStandardAdapterMock.createItem(any())
        }.answers { }

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority(clientRole)))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(duplicateItemPayload)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<ApiItemPayload>()
            .consumeWith { response ->
                assertThat(response.responseBody?.hostId).isEqualTo(duplicateItemPayload.hostId)
                assertThat(response.responseBody?.hostName).isEqualTo(duplicateItemPayload.hostName)
                assertThat(response.responseBody?.description).isEqualTo(duplicateItemPayload.description)
            }
    }

    @Test
    fun `createItem payload with different data but same ID returns DB entry`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority(clientRole)))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(
                duplicateItemPayload.copy(description = "Test")
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<ApiItemPayload>()
            .consumeWith { response ->
                // This value is different in payload, response value should be the same as in DB
                assertThat(response.responseBody?.description).isEqualTo(duplicateItemPayload.description)
            }
    }

    @Test
    fun `createItem with invalid fields returns 400`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority(clientRole)))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testItemPayload.copy(hostId = ""))
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `createItem without user returns 401`() {
        webTestClient
            .mutateWith(csrf())
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testItemPayload)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    @EnabledIfSystemProperty(
        named = "spring.profiles.active",
        matches = "local-dev",
        disabledReason = "Only local-dev has properly configured keycloak & JWT"
    )
    fun `createItem with unauthorized user returns 403`() {
        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_item"), SimpleGrantedAuthority("ROLE_asta")))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testItemPayload)
            .exchange()
            .expectStatus()
            .isForbidden

        webTestClient
            .mutateWith(csrf())
            .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_order"), SimpleGrantedAuthority(clientRole)))
            .post()
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testItemPayload)
            .exchange()
            .expectStatus()
            .isForbidden
    }

    private val testItem = createTestItem()

    private val testItemPayload = testItem.toApiPayload()

    private val duplicateItemPayload = testItemPayload.copy(hostId = "duplicateItemId")

    fun populateDb() {
        runBlocking {
            repository.deleteAll().then(repository.save(duplicateItemPayload.toItem().toMongoItem())).awaitSingle()
        }
    }
}
