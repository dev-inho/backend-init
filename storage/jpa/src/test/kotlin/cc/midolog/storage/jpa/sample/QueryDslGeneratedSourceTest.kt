package cc.midolog.storage.jpa.sample

import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test

class QueryDslGeneratedSourceTest {

    @Test
    fun `QueryDSL generated sources stay in build directory`() {
        val buildDir = Path.of(System.getProperty("user.dir")).resolve("build")
        // Try both standard build layout and root build layout depending on test execution context
        var kaptRoot = Path.of(System.getProperty("user.dir")).resolve("build/generated/source/kapt/main")
        if (!Files.isDirectory(kaptRoot)) {
            val fromProp = System.getProperty("jpaDsl.generatedSourceDir")
            if (fromProp != null) {
                // Try parent of jpadsl generated dir if available, or pass explicitly
                kaptRoot = Path.of(fromProp).parent.parent.parent.parent.parent.parent.parent // Go up from cc/midolog...
            }
        }
        
        // Let's use a system property if we want to be exact, but we might just need to search for one of the files
        // A better approach is to search the entire build directory for the generated files since we know they are in `build/generated/source/kapt/main`.
        
        // Actually, we can use find command or walk the tree
        val expectedFiles = listOf(
            "cc/midolog/storage/jpa/sample/QSampleJpaEntity.java",
            "cc/midolog/storage/jpa/user/QUserJpaEntity.java",
            "cc/midolog/storage/jpa/file/QFileMetaJpaEntity.java",
            "cc/midolog/storage/jpa/jpadsl/fixture/QScalarSampleJpaEntity.java",
            "cc/midolog/storage/jpa/jpadsl/fixture/QRelationParentJpaEntity.java",
            "cc/midolog/storage/jpa/jpadsl/fixture/QRelationChildJpaEntity.java"
        )
        
        val systemPropDir = System.getProperty("queryDsl.generatedSourceDir")
        val generatedRoot = if (systemPropDir != null) {
            Path.of(systemPropDir)
        } else {
            Path.of(System.getProperty("user.dir")).resolve("build/generated/source/kapt/main")
        }

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
