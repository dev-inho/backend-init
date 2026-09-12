package cc.midolog.storage.jpa.sample

import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test

class QueryDslGeneratedSourceTest {

    @Test
    fun `QueryDSL generated sources stay in build directory`() {
        val generatedRoot = Path.of(
            requireNotNull(System.getProperty("queryDsl.generatedSourceDir")) {
                "queryDsl.generatedSourceDir system property must point to Gradle generated source directory"
            }
        )

        val expectedFiles = listOf(
            "cc/midolog/storage/jpa/sample/QSampleJpaEntity.java",
            "cc/midolog/storage/jpa/user/QUserJpaEntity.java",
            "cc/midolog/storage/jpa/file/QFileMetaJpaEntity.java",
            "cc/midolog/storage/jpa/jpadsl/fixture/QScalarSampleJpaEntity.java",
            "cc/midolog/storage/jpa/jpadsl/fixture/QRelationParentJpaEntity.java",
            "cc/midolog/storage/jpa/jpadsl/fixture/QRelationChildJpaEntity.java"
        )

        val missingFiles = expectedFiles
            .map { generatedRoot.resolve(it) }
            .filterNot(Files::isRegularFile)

        assertTrue(missingFiles.isEmpty(), "Missing QueryDSL generated files under $generatedRoot:\n${missingFiles.joinToString("\n")}")
    }

    @Test
    fun `no createQuery in storage jpa src main`() {
        val srcMain = Path.of(System.getProperty("user.dir")).resolve("src/main")

        val sourceFiles = mutableListOf<Path>()
        Files.walk(srcMain).use { stream ->
            stream
                .filter { path ->
                    Files.isRegularFile(path) &&
                        (path.extension == "kt" || path.extension == "java")
                }
                .forEach { sourceFiles.add(it) }
        }

        val violations = mutableListOf<String>()
        for (file in sourceFiles) {
            val lines = Files.readAllLines(file)
            for ((index, line) in lines.withIndex()) {
                if (line.contains("createQuery(")) {
                    violations.add("${file.fileName}:${index + 1}: $line")
                }
            }
        }

        assertTrue(violations.isEmpty(), "Found createQuery( in source files:\n${violations.joinToString("\n")}")
    }
}
