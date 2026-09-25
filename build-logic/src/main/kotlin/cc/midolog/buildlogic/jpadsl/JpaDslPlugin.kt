package cc.midolog.buildlogic.jpadsl

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

open class MyBatisDynamicSqlExtension {
    var generatedSourceDir: String = "generated/sources/mybatisDynamicSql/main/kotlin"
    var domainProjectPath: String = ":core:domain"

    internal val entities: MutableList<JpaEntitySpec> = mutableListOf()

    fun entity(domainClass: String, configure: Action<JpaEntitySpec>) {
        val spec = JpaEntitySpec(domainClass)
        configure.execute(spec)
        validateMyBatisEntitySpec(spec)
        entities += spec
    }

    fun entity(domainClass: String, configure: Closure<*>) {
        val spec = JpaEntitySpec(domainClass)
        configure.delegate = spec
        configure.resolveStrategy = Closure.DELEGATE_FIRST
        configure.call()
        validateMyBatisEntitySpec(spec)
        entities += spec
    }

    internal fun specs(): List<JpaEntitySpec> = entities.toList()
}

fun validateMyBatisEntitySpec(spec: JpaEntitySpec) {
    if (spec.relations.isNotEmpty()) {
        val relationNames = spec.relations.keys.joinToString(", ")
        throw org.gradle.api.GradleException(
            "MyBatis Dynamic SQL does not support JPA relations. Entity '${spec.domainClass}' declares relation(s): $relationNames",
        )
    }
    for ((fieldName, fieldSpec) in spec.fields) {
        if (fieldSpec.converter != null) {
            throw org.gradle.api.GradleException(
                "MyBatis Dynamic SQL does not support JPA converters. Entity '${spec.domainClass}', field '$fieldName' declares converter '${fieldSpec.converter}'",
            )
        }
        if (fieldSpec.relation != null) {
            throw org.gradle.api.GradleException(
                "MyBatis Dynamic SQL does not support JPA relations. Entity '${spec.domainClass}', field '$fieldName' declares relation '${fieldSpec.relation}'",
            )
        }
    }
}

class JpaDslPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val jpaDsl = project.extensions.create("jpaDsl", JpaDslExtension::class.java)
        val mybatisDynamicSql = project.extensions.create("mybatisDynamicSql", MyBatisDynamicSqlExtension::class.java)

        val generatedSourceDir = project.layout.buildDirectory.dir(
            project.provider { jpaDsl.generatedSourceDir },
        )
        val domainSourceDir = project.provider {
            val domainProject = project.findProject(jpaDsl.domainProjectPath) ?: project
            domainProject.layout.projectDirectory.dir("src/main/kotlin")
        }
        val migrationSourceDir = project.provider {
            project.rootProject.layout.projectDirectory.dir(jpaDsl.migrationSourceDir)
        }
        val migrationDraftDir = project.layout.buildDirectory.dir(
            project.provider { jpaDsl.migrationDraftDir },
        )

        val mybatisGeneratedSourceDir = project.layout.buildDirectory.dir(
            project.provider { mybatisDynamicSql.generatedSourceDir },
        )
        val mybatisDomainSourceDir = project.provider {
            val domainProject = project.findProject(mybatisDynamicSql.domainProjectPath) ?: project
            domainProject.layout.projectDirectory.dir("src/main/kotlin")
        }

        val jpaGeneratedResourcesDir = project.layout.buildDirectory.dir("generated/resources/jpaDsl/main")
        val mybatisGeneratedResourcesDir = project.layout.buildDirectory.dir("generated/resources/mybatisDynamicSql/main")

        val generateJpaDslSources = project.tasks.register(
            "generateJpaDslSources",
            GenerateJpaDslSourcesTask::class.java,
        ) { task ->
            task.domainSourceDir.set(domainSourceDir)
            task.outputDir.set(generatedSourceDir)
            task.resourcesOutputDir.set(jpaGeneratedResourcesDir)
            task.specsProvider = { jpaDsl.specs() }
            task.specsFingerprint.set(project.provider { jpaDsl.specs().fingerprint() })
        }
        val generateMyBatisDynamicSqlSources = project.tasks.register(
            "generateMyBatisDynamicSqlSources",
            GenerateMyBatisDynamicSqlSourcesTask::class.java,
        ) { task ->
            task.domainSourceDir.set(mybatisDomainSourceDir)
            task.outputDir.set(mybatisGeneratedSourceDir)
            task.resourcesOutputDir.set(mybatisGeneratedResourcesDir)
            task.specsProvider = { mybatisDynamicSql.specs() }
            task.specsFingerprint.set(project.provider { mybatisDynamicSql.specs().fingerprint() })
        }
        val validateJpaDslGeneratorNegativeCases = project.tasks.register(
            "validateJpaDslGeneratorNegativeCases",
            ValidateJpaDslGeneratorNegativeCasesTask::class.java,
        )
        project.tasks.register("generateMigrationDraft", GenerateMigrationDraftTask::class.java) { task ->
            task.domainSourceDir.set(domainSourceDir)
            task.migrationSourceDir.set(migrationSourceDir)
            task.outputDir.set(migrationDraftDir)
            task.specsProvider = { jpaDsl.specs() }
            task.specsFingerprint.set(project.provider { jpaDsl.specs().fingerprint() })
        }
        project.tasks.register("verifyMigrationDraft", VerifyMigrationDraftTask::class.java) { task ->
            task.domainSourceDir.set(domainSourceDir)
            task.migrationSourceDir.set(migrationSourceDir)
            task.specsProvider = { jpaDsl.specs() }
            task.specsFingerprint.set(project.provider { jpaDsl.specs().fingerprint() })
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.extensions.configure(SourceSetContainer::class.java) { sourceSets ->
                sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME).configure { sourceSet ->
                    val kotlinSource = sourceSet.extensions.getByName("kotlin") as SourceDirectorySet
                    kotlinSource.srcDir(generatedSourceDir)
                    kotlinSource.srcDir(mybatisGeneratedSourceDir)
                }
            }
            project.tasks.matching { it.name == "compileKotlin" }.configureEach { task ->
                task.dependsOn(project.provider {
                    val deps = mutableListOf<Any>()
                    if (jpaDsl.specs().isNotEmpty()) {
                        deps.add(generateJpaDslSources)
                    }
                    if (mybatisDynamicSql.specs().isNotEmpty()) {
                        deps.add(generateMyBatisDynamicSqlSources)
                    }
                    deps
                })
            }
            project.tasks.matching { it.name == "processResources" }.configureEach { task ->
                task.dependsOn(project.provider {
                    val deps = mutableListOf<Any>()
                    if (jpaDsl.specs().isNotEmpty()) {
                        deps.add(generateJpaDslSources)
                    }
                    if (mybatisDynamicSql.specs().isNotEmpty()) {
                        deps.add(generateMyBatisDynamicSqlSources)
                    }
                    deps
                })
                task.doLast {
                    val destDir = (task as? org.gradle.language.jvm.tasks.ProcessResources)?.destinationDir
                        ?: (task as? org.gradle.api.tasks.Copy)?.destinationDir
                    if (destDir != null) {
                        val targetImportsFile = destDir.resolve("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                        val existingLines = if (targetImportsFile.exists()) {
                            targetImportsFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                        } else {
                            mutableSetOf()
                        }

                        val jpaImports = jpaGeneratedResourcesDir.get().asFile.resolve("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                        if (jpaImports.exists()) {
                            existingLines.addAll(jpaImports.readLines().map { it.trim() }.filter { it.isNotEmpty() })
                        }

                        val mybatisImports = mybatisGeneratedResourcesDir.get().asFile.resolve("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                        if (mybatisImports.exists()) {
                            existingLines.addAll(mybatisImports.readLines().map { it.trim() }.filter { it.isNotEmpty() })
                        }

                        if (existingLines.isNotEmpty()) {
                            targetImportsFile.parentFile.mkdirs()
                            targetImportsFile.writeText(existingLines.sorted().joinToString("\n") + "\n")
                        }
                    }
                }
            }
            project.tasks.matching { it.name == "sourcesJar" }.configureEach { task ->
                task.dependsOn(project.provider {
                    val deps = mutableListOf<Any>()
                    if (jpaDsl.specs().isNotEmpty()) {
                        deps.add(generateJpaDslSources)
                    }
                    if (mybatisDynamicSql.specs().isNotEmpty()) {
                        deps.add(generateMyBatisDynamicSqlSources)
                    }
                    deps
                })
            }
        }

        project.tasks.withType(Test::class.java).configureEach { test ->
            test.doFirst {
                test.systemProperty("jpaDsl.generatedSourceDir", generatedSourceDir.get().asFile.absolutePath)
                test.systemProperty("mybatisDynamicSql.generatedSourceDir", mybatisGeneratedSourceDir.get().asFile.absolutePath)
            }
        }
        project.tasks.matching { it.name == "check" }.configureEach { task ->
            task.dependsOn(validateJpaDslGeneratorNegativeCases)
        }
    }
}
