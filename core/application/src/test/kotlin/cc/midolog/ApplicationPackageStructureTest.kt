package cc.midolog

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationPackageStructureTest {

    private fun resolveModuleRoot(): Path {
        val current = Path.of("").toAbsolutePath()
        return if (Files.exists(current.resolve("core").resolve("application").resolve("src"))) {
            current.resolve("core").resolve("application")
        } else {
            current
        }
    }

    @Test
    fun `application package structure forbids common and requires infra`() {
        val moduleRoot = resolveModuleRoot()
        val mainCommonDir = moduleRoot.resolve("src").resolve("main").resolve("kotlin").resolve("cc").resolve("midolog").resolve("common")
        val testCommonDir = moduleRoot.resolve("src").resolve("test").resolve("kotlin").resolve("cc").resolve("midolog").resolve("common")
        val mainInfraDir = moduleRoot.resolve("src").resolve("main").resolve("kotlin").resolve("cc").resolve("midolog").resolve("infra")

        assertFalse(
            Files.exists(mainCommonDir),
            "common package directory in main source must not exist: $mainCommonDir",
        )
        assertFalse(
            Files.exists(testCommonDir),
            "common package directory in test source must not exist: $testCommonDir",
        )
        assertTrue(
            Files.isDirectory(mainInfraDir),
            "infra package directory in main source must exist: $mainInfraDir",
        )
    }
}
