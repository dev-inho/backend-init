package cc.midolog.buildlogic.jpadsl

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class GenerateMyBatisDynamicSqlSourcesTaskTest {

    @Test
    fun `generateMyBatisDynamicSqlSources generates dynamic sql support with mapped columns and nullability`() {
        val projectDir = Files.createTempDirectory("mybatis-dynamic-sql-test")
        writeSettings(projectDir)
        writeFileMetaDomain(projectDir)
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id 'base'
                id 'cc.midolog.jpa-dsl'
            }

            mybatisDynamicSql {
                entity('cc.midolog.file.model.FileMeta') {
                    table = 'file_meta'
                    id = 'id'
                    field('ownerId') { column = 'owner_id' }
                    field('storageKey') { column = 'storage_key' }
                    field('status') { enumStrategy = 'STRING' }
                    field('sizeBytes') { column = 'size_bytes'; nullable = true }
                    field('contentType') { column = 'content_type'; nullable = true }
                    field('checksum') { nullable = true }
                    field('createdAt') { column = 'created_at' }
                    field('updatedAt') { column = 'updated_at' }
                }
            }
            """.trimIndent(),
        )

        val result = gradle(projectDir, "generateMyBatisDynamicSqlSources").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateMyBatisDynamicSqlSources")?.outcome)
        val generatedRoot = projectDir.resolve("build/generated/sources/mybatisDynamicSql/main/kotlin")
        val supportFile = generatedRoot.resolve("cc/midolog/storage/mybatis/file/FileMetaDynamicSqlSupport.kt")
        assertTrue(supportFile.exists(), "FileMetaDynamicSqlSupport.kt should be generated")

        val content = supportFile.readText()

        data class ExpectedColumn(
            val propertyName: String,
            val columnName: String,
            val rawType: String,
            val nullable: Boolean,
            val jdbcType: String,
        )

        val expectedColumns = listOf(
            ExpectedColumn("id", "id", "String", false, "VARCHAR"),
            ExpectedColumn("ownerId", "owner_id", "String", false, "VARCHAR"),
            ExpectedColumn("storageKey", "storage_key", "String", false, "VARCHAR"),
            ExpectedColumn("sizeBytes", "size_bytes", "Long", true, "BIGINT"),
            ExpectedColumn("contentType", "content_type", "String", true, "VARCHAR"),
            ExpectedColumn("checksum", "checksum", "String", true, "VARCHAR"),
            ExpectedColumn("status", "status", "FileStatus", false, "VARCHAR"),
            ExpectedColumn("createdAt", "created_at", "Instant", false, "TIMESTAMP"),
            ExpectedColumn("updatedAt", "updated_at", "Instant", false, "TIMESTAMP"),
        )

        val columnRegex = Regex(
            """val\s+(\w+)\s*:\s*SqlColumn<([^>]+)>\s*=\s*column\(\s*"([^"]+)"\s*,\s*JDBCType\.(\w+)\s*\)""",
        )

        val matches = columnRegex.findAll(content).toList()
        assertEquals(9, matches.size, "Expected 9 column definitions in FileMeta table, found: ${matches.size}")

        matches.forEachIndexed { index, matchResult ->
            val expected = expectedColumns[index]
            val propName = matchResult.groupValues[1]
            val typeStr = matchResult.groupValues[2]
            val colName = matchResult.groupValues[3]
            val jdbcType = matchResult.groupValues[4]

            assertEquals(expected.propertyName, propName, "Property name mismatch at index $index")
            assertEquals(expected.columnName, colName, "Column name mismatch for $propName")
            val isNullable = typeStr.endsWith("?")
            assertEquals(expected.nullable, isNullable, "Nullable mismatch for $propName (type: $typeStr)")
            val actualRawType = typeStr.removeSuffix("?")
            assertEquals(expected.rawType, actualRawType, "Type mismatch for $propName")
            assertEquals(expected.jdbcType, jdbcType, "JDBCType mismatch for $propName")
        }
    }

    @Test
    fun `generateMyBatisDynamicSqlSources fails when entity declares jpa converter`() {
        val projectDir = Files.createTempDirectory("mybatis-dynamic-sql-converter-test")
        writeSettings(projectDir)
        writeFileMetaDomain(projectDir)
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id 'base'
                id 'cc.midolog.jpa-dsl'
            }

            mybatisDynamicSql {
                entity('cc.midolog.file.model.FileMeta') {
                    table = 'file_meta'
                    id = 'id'
                    field('storageKey') {
                        column = 'storage_key'
                        converter = 'cc.midolog.storage.jpa.sample.ScalarSampleCodeJpaConverter'
                    }
                }
            }
            """.trimIndent(),
        )

        val result = gradle(projectDir, "generateMyBatisDynamicSqlSources").buildAndFail()

        val output = result.output
        assertTrue(
            output.contains("converter") && output.contains("not support"),
            "Expected failure message rejecting converter in MyBatis generator, but got:\n$output",
        )
    }

    @Test
    fun `generateMyBatisDynamicSqlSources fails when entity declares jpa relation`() {
        val projectDir = Files.createTempDirectory("mybatis-dynamic-sql-relation-test")
        writeSettings(projectDir)
        writeFileMetaDomain(projectDir)
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id 'base'
                id 'cc.midolog.jpa-dsl'
            }

            mybatisDynamicSql {
                entity('cc.midolog.file.model.FileMeta') {
                    table = 'file_meta'
                    id = 'id'
                    relation('children') {
                        type = 'oneToMany'
                        target = 'cc.midolog.file.model.FileMeta'
                    }
                }
            }
            """.trimIndent(),
        )

        val result = gradle(projectDir, "generateMyBatisDynamicSqlSources").buildAndFail()

        val output = result.output
        assertTrue(
            output.contains("relation") && output.contains("not support"),
            "Expected failure message rejecting relation in MyBatis generator, but got:\n$output",
        )
    }

    @Test
    fun `generateMyBatisDynamicSqlSources generates dynamic sql support for customer extension entity`() {
        val projectDir = Files.createTempDirectory("mybatis-dynamic-sql-customer-test")
        writeSettings(projectDir)
        projectDir.resolve("src/main/kotlin").createDirectories()
        val customerSourceDir = projectDir.resolve("customers/acme/src/main/kotlin/cc/midolog/customers/acme/model")
        customerSourceDir.createDirectories()
        customerSourceDir.resolve("AcmeOrderNote.kt").writeText(
            """
            package cc.midolog.customers.acme.model

            import java.time.Instant

            data class AcmeOrderNote(
                val id: String,
                val customerId: String,
                val note: String,
                val createdAt: Instant,
            )
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id 'base'
                id 'cc.midolog.jpa-dsl'
            }

            mybatisDynamicSql {
                entity('cc.midolog.customers.acme.model.AcmeOrderNote') {
                    table = 'acme_order_note'
                    id = 'id'
                    field('customerId') { column = 'customer_id' }
                    field('note') { column = 'note' }
                    field('createdAt') { column = 'created_at' }
                }
            }
            """.trimIndent(),
        )

        val result = gradle(projectDir, "generateMyBatisDynamicSqlSources").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateMyBatisDynamicSqlSources")?.outcome)
        val generatedRoot = projectDir.resolve("build/generated/sources/mybatisDynamicSql/main/kotlin")
        val supportFile = generatedRoot.resolve("cc/midolog/storage/mybatis/customers/acme/AcmeOrderNoteDynamicSqlSupport.kt")
        assertTrue(supportFile.exists(), "AcmeOrderNoteDynamicSqlSupport.kt should be generated")
        val content = supportFile.readText()
        assertTrue(content.contains("class AcmeOrderNote : SqlTable(\"acme_order_note\")"))
        assertTrue(content.contains("val customerId: SqlColumn<String>"))
    }

    private fun gradle(projectDir: Path, vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(*arguments)

    private fun writeSettings(projectDir: Path) {
        projectDir.resolve("settings.gradle").writeText("""rootProject.name = "fixture"""")
    }

    private fun writeFileMetaDomain(projectDir: Path) {
        val sourceDir = projectDir.resolve("src/main/kotlin/cc/midolog/file/model")
        sourceDir.createDirectories()
        sourceDir.resolve("FileMeta.kt").writeText(
            """
            package cc.midolog.file.model

            import java.time.Instant

            enum class FileStatus { PENDING, READY, FAILED }

            data class FileMeta(
                val id: String,
                val ownerId: String,
                val storageKey: String,
                val sizeBytes: Long?,
                val contentType: String?,
                val checksum: String?,
                val status: FileStatus,
                val createdAt: Instant,
                val updatedAt: Instant,
            )
            """.trimIndent(),
        )
    }
}
