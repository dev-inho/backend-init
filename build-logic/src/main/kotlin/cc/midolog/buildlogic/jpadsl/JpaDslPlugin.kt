package cc.midolog.buildlogic.jpadsl

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test

open class JpaDslPluginExtension {
    var phase: String = "scaffold"
}

abstract class JpaDslPluginInfoTask : DefaultTask() {
    init {
        group = "jpa dsl"
        description = "Reports the internal JPA DSL plugin scaffold status."
    }

    @TaskAction
    fun report() {
        logger.lifecycle("cc.midolog.jpa-dsl plugin scaffold is applied to ${project.path}")
    }
}

abstract class ValidateJpaDslPluginScaffoldTask : DefaultTask() {
    init {
        group = "verification"
        description = "Validates the internal JPA DSL plugin scaffold configuration."
    }

    @TaskAction
    fun validate() {
        val extension = project.extensions.getByType(JpaDslPluginExtension::class.java)
        require(extension.phase.isNotBlank()) {
            "jpaDslPlugin.phase must not be blank for ${project.path}"
        }
    }
}

class JpaDslPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("jpaDslPlugin", JpaDslPluginExtension::class.java)
        val jpaDsl = project.extensions.create("jpaDsl", JpaDslExtension::class.java)
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

        project.tasks.register("jpaDslPluginInfo", JpaDslPluginInfoTask::class.java)
        project.tasks.register("validateJpaDslPluginScaffold", ValidateJpaDslPluginScaffoldTask::class.java)
        val generateJpaDslSources = project.tasks.register(
            "generateJpaDslSources",
            GenerateJpaDslSourcesTask::class.java,
        ) { task ->
            task.domainSourceDir.set(domainSourceDir)
            task.outputDir.set(generatedSourceDir)
            task.specsProvider = { jpaDsl.specs() }
            task.specsFingerprint.set(project.provider { jpaDsl.specs().fingerprint() })
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
                }
            }
            project.tasks.matching { it.name == "compileKotlin" }.configureEach { task ->
                task.dependsOn(generateJpaDslSources)
            }
        }

        project.tasks.withType(Test::class.java).configureEach { test ->
            test.doFirst {
                test.systemProperty("jpaDsl.generatedSourceDir", generatedSourceDir.get().asFile.absolutePath)
            }
        }
        project.tasks.matching { it.name == "check" }.configureEach { task ->
            task.dependsOn(validateJpaDslGeneratorNegativeCases)
        }
    }
}
