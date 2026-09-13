package cc.midolog.customer

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class CustomerBoundaryGuardTest {

    private val targetModules = listOf("core", "support")
    private val forbiddenImportPrefixes = listOf(
        "cc.midolog.customers",
    )

    private fun resolveRepositoryRoot(): Path {
        var candidate: Path? = Path.of("").toAbsolutePath()
        while (candidate != null) {
            val hasSettings = Files.exists(candidate.resolve("settings.gradle")) ||
                Files.exists(candidate.resolve("settings.gradle.kts"))
            val hasBaseModules = listOf("core", "gateway", "support", "client")
                .all { Files.isDirectory(candidate.resolve(it)) }
            if (hasSettings && hasBaseModules) {
                return candidate.normalize()
            }
            candidate = candidate.parent
        }
        throw AssertionError("Failed to resolve repository root containing settings.gradle and base modules")
    }

    @Test
    fun `core and support modules must not import customer specific packages`() {
        val repoRoot = resolveRepositoryRoot()
        val sourceDirs = mutableListOf<Path>()

        for (module in targetModules) {
            val moduleRoot = repoRoot.resolve(module)
            if (!Files.isDirectory(moduleRoot)) continue
            Files.walk(moduleRoot).use { stream ->
                stream
                    .filter { path -> Files.isDirectory(path) && isSourceDir(path) }
                    .forEach { sourceDirs.add(it) }
            }
        }

        assertTrue(sourceDirs.isNotEmpty(), "Source directories must exist in core and support")

        val sourceFiles = mutableListOf<Path>()
        for (sourceDir in sourceDirs) {
            Files.walk(sourceDir).use { stream ->
                stream
                    .filter { path ->
                        Files.isRegularFile(path) &&
                            (path.extension == "kt" || path.extension == "java") &&
                            !isExcludedPath(path)
                    }
                    .forEach { sourceFiles.add(it) }
            }
        }

        val violations = mutableListOf<String>()

        for (file in sourceFiles) {
            val relativePath = repoRoot.relativize(file).toString().replace('\\', '/')
            val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
            for ((index, rawLine) in lines.withIndex()) {
                val trimmed = rawLine.trimStart()
                if (trimmed.startsWith("import ")) {
                    var importStatement = trimmed.removePrefix("import ").trimStart()
                    if (importStatement.startsWith("static ")) {
                        importStatement = importStatement.removePrefix("static ").trimStart()
                    }
                    if (importStatement.endsWith(";")) {
                        importStatement = importStatement.removeSuffix(";").trimEnd()
                    }

                    val isForbidden = forbiddenImportPrefixes.any { prefix ->
                        importStatement == prefix || importStatement.startsWith("$prefix.")
                    }

                    if (isForbidden) {
                        val lineNumber = index + 1
                        violations.add("$relativePath:$lineNumber: $rawLine")
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Customer boundary violated: core and support must not import customer specific code (${violations.size} violations found):\n" +
                violations.joinToString("\n")
        )
    }

    private fun isSourceDir(path: Path): Boolean {
        if (isExcludedPath(path)) return false
        val nameCount = path.nameCount
        if (nameCount >= 2) {
            val lastTwo = path.subpath(nameCount - 2, nameCount).toString().replace('\\', '/')
            if (lastTwo == "src/main" || lastTwo == "src/test" || lastTwo == "src/testFixtures") {
                return true
            }
        }
        return false
    }

    private fun isExcludedPath(path: Path): Boolean {
        for (part in path) {
            val name = part.toString()
            if (name == "build" || name == "generated" || name == ".gradle" || name == ".git") {
                return true
            }
        }
        return false
    }
}
