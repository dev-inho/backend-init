package cc.midolog.gateway.route

import cc.midolog.gateway.config.GatewayRouteProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GatewayRouteSelectorTest {

    @Test
    fun `single application url remains the default target`() {
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(
                applicationUrl = "http://application.internal",
                batchUrl = "http://batch.internal",
            ),
        )

        assertEquals("http://application.internal", selector.selectTarget("/api/users"))
        assertEquals("http://application.internal", selector.selectTarget("/actuator/health"))
        assertEquals("http://batch.internal", selector.selectTarget("/batch/jobs"))
    }

    @Test
    fun `multiple application urls are selected round robin`() {
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(
                applicationUrl = "http://fallback.internal",
                applicationUrls = listOf(
                    "http://application-a.internal",
                    "http://application-b.internal",
                ),
                batchUrl = "http://batch.internal",
            ),
        )

        assertEquals("http://application-a.internal", selector.selectTarget("/api/users"))
        assertEquals("http://application-b.internal", selector.selectTarget("/api/users"))
        assertEquals("http://application-a.internal", selector.selectTarget("/actuator/health"))
        assertEquals("http://batch.internal", selector.selectTarget("/batch/jobs"))
    }

    @Test
    fun `invalid route url fails fast with a clear message`() {
        val error = assertFailsWith<IllegalStateException> {
            GatewayRouteSelector(
                GatewayRouteProperties(
                    applicationUrl = "application.internal",
                    batchUrl = "http://batch.internal",
                ),
            )
        }

        assertEquals(
            "gateway.routes.application-url must be an absolute http(s) URL: application.internal",
            error.message,
        )
    }
}
