package cc.midolog.web.sample

import cc.midolog.business.service.SampleService
import cc.midolog.business.service.UserService
import cc.midolog.infra.security.JwtProvider
import cc.midolog.sample.model.Sample
import cc.midolog.user.model.User
import cc.midolog.web.handler.GlobalExceptionHandler
import cc.midolog.web.sample.dto.CreateSampleRequest
import cc.midolog.web.user.UserController
import cc.midolog.web.user.dto.CreateUserRequest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(controllers = [SampleController::class, UserController::class])
@Import(GlobalExceptionHandler::class)
class SampleControllerCreateStatusTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var sampleService: SampleService

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @Test
    fun `POST create sample endpoint returns 201 CREATED status`() = runTest {
        val request = CreateSampleRequest(id = "sample-1", name = "Test Sample")
        val sample = Sample(id = "sample-1", name = "Test Sample")
        given(sampleService.save(sample)).willReturn(sample)

        webTestClient.post()
            .uri("/api/sample")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.data.id").isEqualTo("sample-1")
    }

    @Test
    fun `POST create user endpoint returns 201 CREATED status`() = runTest {
        val request = CreateUserRequest(id = "user-1", email = "user@test.com", displayName = "User One")
        val user = User(id = "user-1", email = "user@test.com", displayName = "User One")
        given(userService.save(user)).willReturn(user)

        webTestClient.post()
            .uri("/api/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.data.id").isEqualTo("user-1")
    }
}
