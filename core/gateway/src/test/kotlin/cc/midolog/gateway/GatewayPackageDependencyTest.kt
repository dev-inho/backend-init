package cc.midolog.gateway

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class GatewayPackageDependencyTest {

    @Test
    fun `config package does not import handler or route`() {
        val configRoot = Path.of("src/main/kotlin/cc/midolog/gateway/config")
        val forbiddenTokens = listOf(
            "import cc.midolog.gateway.handler.",
            "import cc.midolog.gateway.proxy.",
            "import cc.midolog.gateway.route.",
        )

        val violations = Files.walk(configRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { path ->
                    val text = path.readText()
                    forbiddenTokens
                        .filter { token -> text.contains(token) }
                        .map { token -> "$path contains forbidden import '$token'" }
                        .stream()
                }
                .toList()
        }

        assertTrue(violations.isEmpty(), "Dependency cycle detected:\n" + violations.joinToString(separator = "\n"))
    }
}
