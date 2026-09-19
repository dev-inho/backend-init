package cc.midolog.buildlogic.publishing

import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RemoteArtifactPreflightCheckerTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private val checker = RemoteArtifactPreflightChecker(allowInsecureTestUrl = true)

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private val sampleArtifacts = listOf(
        RemoteArtifactPreflightChecker.TargetArtifact("cc.midolog", "backend-init-bom", "1.0.0"),
        RemoteArtifactPreflightChecker.TargetArtifact("cc.midolog", "backend-init-core", "1.0.0"),
    )

    @Test
    fun `when artifact already exists with 200 OK then preflight fails fast preventing overwriting`() {
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }

        val ex = assertThrows(GradleException::class.java) {
            checker.checkAll(
                baseUrl = "http://127.0.0.1:$port/dev-inho/backend-init",
                username = "test-user",
                token = "test-token",
                artifacts = sampleArtifacts,
            )
        }

        assertTrue(ex.message!!.contains("Remote release artifact already exists"))
        assertTrue(ex.message!!.contains("Overwriting existing releases is strictly prohibited"))
    }

    @Test
    fun `when all artifacts return 404 Not Found then preflight passes cleanly`() {
        val requestCount = AtomicInteger(0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }

        // 예외 없이 정상 통과해야 함
        checker.checkAll(
            baseUrl = "http://127.0.0.1:$port/dev-inho/backend-init",
            username = "test-user",
            token = "test-token",
            artifacts = sampleArtifacts,
        )

        assertEquals(2, requestCount.get(), "모든 아티팩트에 대해 HEAD 요청이 수행되어야 합니다.")
    }

    @Test
    fun `when server returns 401 Unauthorized then preflight fails and does not treat as 404`() {
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        }

        val ex = assertThrows(GradleException::class.java) {
            checker.checkAll(
                baseUrl = "http://127.0.0.1:$port/dev-inho/backend-init",
                username = "test-user",
                token = "invalid-token",
                artifacts = sampleArtifacts,
            )
        }

        assertTrue(ex.message!!.contains("authentication/authorization failure"))
        assertTrue(ex.message!!.contains("401"))
    }

    @Test
    fun `when server returns 403 Forbidden then preflight fails and does not treat as 404`() {
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }

        val ex = assertThrows(GradleException::class.java) {
            checker.checkAll(
                baseUrl = "http://127.0.0.1:$port/dev-inho/backend-init",
                username = "test-user",
                token = "forbidden-token",
                artifacts = sampleArtifacts,
            )
        }

        assertTrue(ex.message!!.contains("authentication/authorization failure"))
        assertTrue(ex.message!!.contains("403"))
    }

    @Test
    fun `when server returns 500 Internal Server Error then preflight fails and does not treat as 404`() {
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }

        val ex = assertThrows(GradleException::class.java) {
            checker.checkAll(
                baseUrl = "http://127.0.0.1:$port/dev-inho/backend-init",
                username = "test-user",
                token = "test-token",
                artifacts = sampleArtifacts,
            )
        }

        assertTrue(ex.message!!.contains("unexpected HTTP status 500"))
    }

    @Test
    fun `when server returns 302 Redirect then redirect is not followed and fails to prevent token leak`() {
        server.createContext("/") { exchange ->
            exchange.responseHeaders.set("Location", "http://attacker.com/sink")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        val ex = assertThrows(SecurityException::class.java) {
            checker.checkAll(
                baseUrl = "http://127.0.0.1:$port/dev-inho/backend-init",
                username = "test-user",
                token = "secret-token",
                artifacts = sampleArtifacts,
            )
        }

        assertTrue(ex.message!!.contains("Unexpected redirect"))
        assertTrue(ex.message!!.contains("credential leakage"))
    }

    @Test
    fun `preflight sends correct Basic authorization credentials header`() {
        val capturedAuth = AtomicReference<String>()
        server.createContext("/") { exchange ->
            capturedAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }

        checker.checkAll(
            baseUrl = "http://127.0.0.1:$port/dev-inho/backend-init",
            username = "midolog-user",
            token = "super-secret-token",
            artifacts = listOf(sampleArtifacts.first()),
        )

        val expectedHeader = "Basic " + Base64.getEncoder().encodeToString("midolog-user:super-secret-token".toByteArray(Charsets.UTF_8))
        assertEquals(expectedHeader, capturedAuth.get())
    }
}
