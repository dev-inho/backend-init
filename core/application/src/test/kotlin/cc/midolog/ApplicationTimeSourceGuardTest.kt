package cc.midolog

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class ApplicationTimeSourceGuardTest {

    private fun resolveModuleRoot(): Path {
        val current = Path.of("").toAbsolutePath()
        return if (Files.exists(current.resolve("core").resolve("application").resolve("src"))) {
            current.resolve("core").resolve("application")
        } else {
            current
        }
    }

    @Test
    fun `application main source stays free of Instant now direct calls`() {
        val moduleRoot = resolveModuleRoot()
        val mainSourceRoot = moduleRoot.resolve("src").resolve("main").resolve("kotlin")
        val forbiddenToken = "Instant.now()"

        assertTrue(Files.isDirectory(mainSourceRoot), "application main source directory should exist: $mainSourceRoot")

        val violations = Files.walk(mainSourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { path ->
                    val text = path.readText()
                    if (text.contains(forbiddenToken)) {
                        listOf("$path contains $forbiddenToken").stream()
                    } else {
                        emptyList<String>().stream()
                    }
                }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "Instant.now() must not be called directly in core:application main source:\n" + violations.joinToString("\n")
        )
    }
}
