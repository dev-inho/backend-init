package cc.midolog.storage.jpa.sample

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList

class SelfContainedQueryDslGuardTest {

    @Test
    fun `QueryDSL generated classes should exist and string JPQL should be banned`() {
        assertAll(
            {
                // 1. Check for Q*JpaEntity.java
                val generatedSourceDir = System.getProperty("queryDsl.generatedSourceDir")
                    ?: error("System property queryDsl.generatedSourceDir is not set")

                val expectedClasses = listOf(
                    "QSampleJpaEntity.java",
                    "QUserJpaEntity.java",
                    "QFileMetaJpaEntity.java",
                    "QScalarSampleJpaEntity.java",
                    "QRelationParentJpaEntity.java",
                    "QRelationChildJpaEntity.java"
                )

                val generatedDir = File(generatedSourceDir)
                assertTrue(generatedDir.exists(), "Generated source directory does not exist: $generatedDir")

                val actualFiles = generatedDir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".java") }
                    .map { it.name }
                    .toList()

                for (expected in expectedClasses) {
                    assertTrue(
                        actualFiles.contains(expected),
                        "Expected generated QueryDSL class $expected not found in $generatedSourceDir"
                    )
                }
            },
            {
                // 2. Scan storage/jpa/src/main for "createQuery("
                val mainSourceDir = Paths.get("src/main")
                val rootDir = if (Files.exists(mainSourceDir)) {
                    mainSourceDir
                } else {
                    val alternate = Paths.get("storage/jpa/src/main")
                    if (Files.exists(alternate)) alternate else error("Could not find src/main or storage/jpa/src/main")
                }

                val filesWithCreateQuery = Files.walk(rootDir)
                    .filter { Files.isRegularFile(it) }
                    .filter { it.toString().endsWith(".kt") || it.toString().endsWith(".java") }
                    .filter { file ->
                        Files.readAllLines(file).any { line ->
                            line.contains("createQuery(")
                        }
                    }
                    .toList()

                assertTrue(
                    filesWithCreateQuery.isEmpty(),
                    "Found 'createQuery(' in files: \n${filesWithCreateQuery.joinToString("\n")}"
                )
            }
        )
    }
}
