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
                    "cc/midolog/storage/jpa/sample/QSampleJpaEntity.java",
                    "cc/midolog/storage/jpa/user/QUserJpaEntity.java",
                    "cc/midolog/storage/jpa/file/QFileMetaJpaEntity.java",
                    "cc/midolog/storage/jpa/jpadsl/fixture/QScalarSampleJpaEntity.java",
                    "cc/midolog/storage/jpa/jpadsl/fixture/QRelationParentJpaEntity.java",
                    "cc/midolog/storage/jpa/jpadsl/fixture/QRelationChildJpaEntity.java"
                )

                val generatedDir = File(generatedSourceDir)
                // If it doesn't exist, we fail immediately to show the error
                assertTrue(generatedDir.exists(), "Generated source directory does not exist: $generatedDir")

                for (expected in expectedClasses) {
                    val actualFile = File(generatedDir, expected)
                    assertTrue(
                        actualFile.exists() && actualFile.isFile,
                        "Expected generated QueryDSL class file not found at exact path: $expected"
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
