package cc.midolog.web

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class WebTimeSourceGuardTest {

    private fun resolveModuleRoot(): Path {
        val current = Path.of("").toAbsolutePath()
        return if (Files.exists(current.resolve("support").resolve("web").resolve("src"))) {
            current.resolve("support").resolve("web")
        } else {
            current
        }
    }

    @Test
    fun `web main source stays free of direct time source calls`() {
        val moduleRoot = resolveModuleRoot()
        val mainSourceRoot = moduleRoot.resolve("src").resolve("main").resolve("kotlin")
        val forbiddenTokens = listOf(
            "System.currentTimeMillis()",
            "Instant.now()",
            "Date()"
        )

        assertTrue(Files.isDirectory(mainSourceRoot), "web main source directory should exist: $mainSourceRoot")

        val violations = Files.walk(mainSourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { path ->
                    val text = path.readText()
                    forbiddenTokens.filter { text.contains(it) }.map { token ->
                        "$path contains $token"
                    }.stream()
                }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "Direct time source calls must not be present in support:web main source:\n" + violations.joinToString("\n")
        )
    }
}
