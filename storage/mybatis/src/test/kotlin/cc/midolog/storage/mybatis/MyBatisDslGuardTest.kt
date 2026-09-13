package cc.midolog.storage.mybatis

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MyBatisDslGuardTest {

    @Test
    fun `shared domain entities DSL should not contain JPA fixtures or JPA-only configurations`() {
        val rootPath = findProjectRoot()
        val dslScript = File(rootPath, "gradle/domain-entities.gradle")
        assertTrue(dslScript.exists(), "gradle/domain-entities.gradle must exist at ${dslScript.absolutePath}")

        val content = dslScript.readText()

        assertAll(
            {
                assertFalse(
                    content.contains("jpadsl.fixture"),
                    "gradle/domain-entities.gradle must not contain jpadsl.fixture entities (found in shared DSL)",
                )
            },
            {
                assertFalse(
                    content.contains("ScalarSample"),
                    "gradle/domain-entities.gradle must not contain ScalarSample fixture",
                )
            },
            {
                assertFalse(
                    content.contains("RelationParent"),
                    "gradle/domain-entities.gradle must not contain RelationParent fixture",
                )
            },
            {
                assertFalse(
                    content.contains("RelationChild"),
                    "gradle/domain-entities.gradle must not contain RelationChild fixture",
                )
            },
            {
                assertFalse(
                    content.contains("converter"),
                    "gradle/domain-entities.gradle must not declare JPA converter",
                )
            },
            {
                assertFalse(
                    content.contains("relation"),
                    "gradle/domain-entities.gradle must not declare JPA relation blocks",
                )
            },
        )
    }

    @Test
    fun `generated mybatis dynamic sql classes should only contain domain entities and no JPA fixtures`() {
        val generatedSourceDir = System.getProperty("mybatisDynamicSql.generatedSourceDir")
            ?: error("System property mybatisDynamicSql.generatedSourceDir is not set")
        val generatedDir = File(generatedSourceDir)
        assertTrue(generatedDir.exists(), "Generated source directory does not exist: $generatedDir")

        val expectedDomainClasses = listOf(
            "cc/midolog/storage/mybatis/sample/SampleDynamicSqlSupport.kt",
            "cc/midolog/storage/mybatis/user/UserDynamicSqlSupport.kt",
            "cc/midolog/storage/mybatis/file/FileMetaDynamicSqlSupport.kt",
        )

        val forbiddenFixtureClasses = listOf(
            "cc/midolog/storage/mybatis/jpadsl/fixture/ScalarSampleDynamicSqlSupport.kt",
            "cc/midolog/storage/mybatis/jpadsl/fixture/RelationParentDynamicSqlSupport.kt",
            "cc/midolog/storage/mybatis/jpadsl/fixture/RelationChildDynamicSqlSupport.kt",
        )

        assertAll(
            {
                for (expected in expectedDomainClasses) {
                    val file = File(generatedDir, expected)
                    assertTrue(file.exists() && file.isFile, "Expected generated MyBatis Dynamic SQL class not found: $expected")
                }
            },
            {
                for (forbidden in forbiddenFixtureClasses) {
                    val file = File(generatedDir, forbidden)
                    assertFalse(
                        file.exists(),
                        "JPA fixture DynamicSqlSupport must NOT be generated in MyBatis module: $forbidden",
                    )
                }
            },
            {
                val fixtureDir = File(generatedDir, "cc/midolog/storage/mybatis/jpadsl")
                assertFalse(
                    fixtureDir.exists(),
                    "JPA fixture package directory must NOT exist in MyBatis module: ${fixtureDir.absolutePath}",
                )
            },
        )
    }

    private fun findProjectRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "gradle/domain-entities.gradle").exists()) {
                return current
            }
            current = current.parentFile
        }
        return File("../..").canonicalFile
    }
}
