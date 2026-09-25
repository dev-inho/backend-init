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

class JpaDslPluginTest {

    @Test
    fun `plugin applies and registers generation tasks`() {
        val projectDir = Files.createTempDirectory("jpa-dsl-plugin-test")
        projectDir.resolve("settings.gradle.kts").writeText("""rootProject.name = "fixture"""")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("cc.midolog.jpa-dsl")
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks", "--all")
            .build()

        assertTrue(result.output.contains("generateJpaDslSources"))
        assertTrue(result.output.contains("generateMyBatisDynamicSqlSources"))
    }

    @Test
    fun `plugin fails clearly when applied with an unknown plugin id`() {
        val projectDir = Files.createTempDirectory("jpa-dsl-plugin-failure-test")
        projectDir.resolve("settings.gradle.kts").writeText("""rootProject.name = "fixture"""")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("cc.midolog.jpa-dsl-missing")
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks")
            .buildAndFail()

        assertTrue(result.output.contains("Plugin [id: 'cc.midolog.jpa-dsl-missing'] was not found"))
    }

    @Test
    fun `typed Groovy DSL generates entity repository and mapper files`() {
        val projectDir = Files.createTempDirectory("jpa-dsl-plugin-generation-test")
        writeSettings(projectDir)
        writeDomain(projectDir, "data class Sample(\n    val id: String,\n    val name: String,\n)\n")
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id 'base'
                id 'cc.midolog.jpa-dsl'
            }

            jpaDsl {
                entity('cc.midolog.sample.model.Sample') {
                    table = 'sample'
                    id = 'id'

                    field('name') {
                        column = 'sample_name'
                    }
                }
            }
            """.trimIndent(),
        )

        val result = gradle(projectDir, "generateJpaDslSources").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateJpaDslSources")?.outcome)
        val generatedRoot = projectDir.resolve("build/generated/sources/jpaDsl/main/kotlin")
        val entity = generatedRoot.resolve("cc/midolog/storage/jpa/sample/SampleJpaEntity.kt")
        assertTrue(entity.exists(), "entity file should be generated")
        assertTrue(entity.readText().contains("@Column(name = \"sample_name\", nullable = false)"))
        assertTrue(generatedRoot.resolve("cc/midolog/storage/jpa/sample/SampleJpaRepository.kt").exists())
        assertTrue(generatedRoot.resolve("cc/midolog/storage/jpa/sample/SampleJpaMapper.kt").exists())
    }

    @Test
    fun `typed DSL supports custom generated output directory and check wiring`() {
        val projectDir = Files.createTempDirectory("jpa-dsl-plugin-hardening-test")
        writeSettings(projectDir)
        writeDomain(projectDir, "data class Sample(\n    val id: String,\n    val name: String,\n)\n")
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id 'base'
                id 'cc.midolog.jpa-dsl'
            }

            jpaDsl {
                generatedSourceDir = 'custom/generated/jpa'

                entity('cc.midolog.sample.model.Sample') {
                    table = 'sample'
                    id = 'id'
                }
            }
            """.trimIndent(),
        )

        val generateResult = gradle(projectDir, "generateJpaDslSources").build()

        assertEquals(TaskOutcome.SUCCESS, generateResult.task(":generateJpaDslSources")?.outcome)
        assertTrue(
            projectDir.resolve("build/custom/generated/jpa/cc/midolog/storage/jpa/sample/SampleJpaEntity.kt").exists(),
            "custom generatedSourceDir should control output location",
        )

        val checkResult = gradle(projectDir, "check").build()

        assertEquals(TaskOutcome.SUCCESS, checkResult.task(":validateJpaDslGeneratorNegativeCases")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, checkResult.task(":check")?.outcome)
    }

    @Test
    fun `typed DSL fails clearly when no entity is declared`() {
        val projectDir = Files.createTempDirectory("jpa-dsl-plugin-empty-test")
        writeSettings(projectDir)
        projectDir.resolve("src/main/kotlin").createDirectories()
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id 'cc.midolog.jpa-dsl'
            }

            jpaDsl {
            }
            """.trimIndent(),
        )

        val result = gradle(projectDir, "generateJpaDslSources").buildAndFail()

        assertTrue(result.output.contains("jpaDsl must declare at least one entity"))
    }

    @Test
    fun `typed DSL validation fails before Kotlin compilation for malformed specs`() {
        val invalidCases = listOf(
            InvalidCase(
                label = "missing-domain",
                domainSource = null,
                dsl = baseDsl("cc.midolog.sample.model.Sample"),
                message = "Domain source not found for cc.midolog.sample.model.Sample",
            ),
            InvalidCase(
                label = "unsupported-constructor",
                domainSource = "class Sample\n",
                dsl = baseDsl("cc.midolog.sample.model.Sample"),
                message = "Only primary-constructor data classes are supported: cc.midolog.sample.model.Sample",
            ),
            InvalidCase(
                label = "missing-id",
                domainSource = sampleDomain(),
                dsl = baseDsl("cc.midolog.sample.model.Sample", id = "missing"),
                message = "ID property 'missing' does not exist in cc.midolog.sample.model.Sample",
            ),
            InvalidCase(
                label = "bad-converter",
                domainSource = sampleDomain(),
                dsl = baseDsl(
                    "cc.midolog.sample.model.Sample",
                    fields = """
                    field('name') {
                        converter = ''
                    }
                    """.trimIndent(),
                ),
                message = "Field 'name' in cc.midolog.sample.model.Sample declares converter without storageType",
            ),
            InvalidCase(
                label = "unsupported-enum",
                domainSource = sampleDomain(),
                dsl = baseDsl(
                    "cc.midolog.sample.model.Sample",
                    fields = """
                    field('name') {
                        enumStrategy = 'ORDINAL'
                    }
                    """.trimIndent(),
                ),
                message = "Field 'name' in cc.midolog.sample.model.Sample has unsupported enum strategy 'ORDINAL'",
            ),
            InvalidCase(
                label = "unknown-relation",
                domainSource = sampleDomain(parentId = true),
                dsl = baseDsl(
                    "cc.midolog.sample.model.Sample",
                    fields = """
                    field('parentId') {
                        relation = 'parent'
                    }
                    """.trimIndent(),
                ),
                message = "Field 'parentId' in cc.midolog.sample.model.Sample points to unknown relation 'parent'",
            ),
            InvalidCase(
                label = "malformed-relation",
                domainSource = sampleDomain(parentId = true),
                dsl = baseDsl(
                    "cc.midolog.sample.model.Sample",
                    relations = """
                    relation('parent') {
                        type = 'manyToOne'
                        target = 'cc.midolog.sample.model.Parent'
                        sourceField = 'parentId'
                    }
                    """.trimIndent(),
                ),
                message = "Relation 'parent' in cc.midolog.sample.model.Sample must declare joinColumn",
            ),
            InvalidCase(
                label = "self-target-relation",
                domainSource = sampleDomain(parentId = true),
                dsl = baseDsl(
                    "cc.midolog.sample.model.Sample",
                    relations = """
                    relation('parent') {
                        type = 'manyToOne'
                        target = 'cc.midolog.sample.model.Sample'
                    }
                    """.trimIndent(),
                ),
                message = "Relation 'parent' in cc.midolog.sample.model.Sample cannot target itself",
            ),
        )

        invalidCases.forEach { invalidCase ->
            val projectDir = Files.createTempDirectory("jpa-dsl-plugin-${invalidCase.label}-test")
            writeSettings(projectDir)
            if (invalidCase.domainSource == null) {
                projectDir.resolve("src/main/kotlin").createDirectories()
            } else {
                writeDomain(projectDir, invalidCase.domainSource)
            }
            projectDir.resolve("build.gradle").writeText(
                """
                plugins {
                    id 'cc.midolog.jpa-dsl'
                }

                ${invalidCase.dsl}
                """.trimIndent(),
            )

            val result = gradle(projectDir, "generateJpaDslSources").buildAndFail()

            assertTrue(
                result.output.contains(invalidCase.message),
                "${invalidCase.label} should fail with '${invalidCase.message}', output was:\n${result.output}",
            )
        }
    }

    private fun gradle(projectDir: Path, vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(*arguments)

    private fun writeSettings(projectDir: Path) {
        projectDir.resolve("settings.gradle").writeText("""rootProject.name = "fixture"""")
    }

    private fun writeDomain(projectDir: Path, classBody: String) {
        val sourceDir = projectDir.resolve("src/main/kotlin/cc/midolog/sample/model")
        sourceDir.createDirectories()
        sourceDir.resolve("Sample.kt").writeText(
            """
            package cc.midolog.sample.model

            $classBody
            """.trimIndent(),
        )
    }

    private fun sampleDomain(parentId: Boolean = false): String =
        if (parentId) {
            "data class Sample(\n    val id: String,\n    val parentId: String,\n    val name: String,\n)\n"
        } else {
            "data class Sample(\n    val id: String,\n    val name: String,\n)\n"
        }

    private fun baseDsl(
        domainClass: String,
        id: String = "id",
        fields: String = "",
        relations: String = "",
    ): String =
        """
        jpaDsl {
            entity('$domainClass') {
                table = 'sample'
                id = '$id'
                $fields
                $relations
            }
        }
        """.trimIndent()

    private data class InvalidCase(
        val label: String,
        val domainSource: String?,
        val dsl: String,
        val message: String,
    )
}
