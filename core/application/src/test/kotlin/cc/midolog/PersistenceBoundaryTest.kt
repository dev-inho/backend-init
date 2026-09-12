package cc.midolog

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class PersistenceBoundaryTest {

    private val targetModules = listOf("core", "gateway", "support", "client")

    private val forbiddenImportPrefixes = listOf(
        "jakarta.persistence",
        "org.springframework.data.jpa",
        "org.hibernate",
        "org.mybatis",
        "org.apache.ibatis",
        "cc.midolog.storage.jpa",
        "cc.midolog.storage.mybatis",
    )

    private fun resolveRepositoryRoot(): Path {
        var candidate: Path? = Path.of("").toAbsolutePath()
        while (candidate != null) {
            val hasSettings = Files.exists(candidate.resolve("settings.gradle")) ||
                Files.exists(candidate.resolve("settings.gradle.kts"))
            val hasTargetModules = targetModules.all { Files.isDirectory(candidate.resolve(it)) }
            if (hasSettings && hasTargetModules) {
                return candidate.normalize()
            }
            candidate = candidate.parent
        }
        throw AssertionError("Failed to resolve repository root containing settings.gradle and target modules: $targetModules")
    }

    @Test
    fun `persistence concerns must stay within storage modules`() {
        val repoRoot = resolveRepositoryRoot()

        // 기준 루트 모듈 누락 검증
        for (module in targetModules) {
            val moduleDir = repoRoot.resolve(module)
            assertTrue(Files.isDirectory(moduleDir), "Target module directory must exist: $moduleDir")
        }

        // 실제 존재하는 src/main, src/test, src/testFixtures source dir 수집
        val sourceDirs = mutableListOf<Path>()

        for (module in targetModules) {
            val moduleRoot = repoRoot.resolve(module)
            Files.walk(moduleRoot).use { stream ->
                stream
                    .filter { path ->
                        Files.isDirectory(path) && isSourceDir(path)
                    }
                    .forEach { sourceDirs.add(it) }
            }
        }

        assertTrue(
            sourceDirs.isNotEmpty(),
            "Scan target source directories must not be empty across $targetModules under $repoRoot"
        )

        // 대상 소스 파일 수집 (.kt, .java)
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

        assertTrue(
            sourceFiles.isNotEmpty(),
            "Scan target source files must not be empty across ${sourceDirs.size} source dirs"
        )

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

        violations.sort()

        println(
            "PersistenceBoundaryTest scanned ${sourceDirs.size} source dirs and ${sourceFiles.size} source files. " +
                "Violations: ${violations.size}"
        )

        assertTrue(
            violations.isEmpty(),
            "Persistence boundary violated (${violations.size} violations found):\n" +
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
