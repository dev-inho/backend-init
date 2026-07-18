package cc.midolog.buildlogic.jpadsl

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes a draft `V{next}__generated_draft.sql` describing the additive changes needed to
 * bring the Flyway schema in line with the typed JPA DSL. The draft is written to the build
 * directory only — it is never copied into the migration folder or applied automatically, so
 * a human reviews and promotes it deliberately.
 */
abstract class GenerateMigrationDraftTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val domainSourceDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationSourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val specsFingerprint: Property<String>

    @get:Internal
    var specsProvider: (() -> List<JpaEntitySpec>)? = null

    init {
        group = "jpa dsl"
        description = "Generates a draft Flyway migration from the JPA DSL vs the current schema."
    }

    @TaskAction
    fun generate() {
        val specs = specsProvider?.invoke().orEmpty()
        if (specs.isEmpty()) {
            throw GradleException("jpaDsl must declare at least one entity")
        }
        val migrationDir = migrationSourceDir.get().asFile
        val diff = MigrationDraftEngine.computeDiff(specs, domainSourceDir.get().asFile, migrationDir)

        val outputRoot = outputDir.get().asFile
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()

        val version = MigrationDraftEngine.nextVersion(MigrationDraftEngine.migrationFiles(migrationDir))
        val draftFile = File(outputRoot, "V${version}__generated_draft.sql")
        draftFile.writeText(MigrationDraftRenderer().render(diff))

        if (diff.isEmpty) {
            logger.lifecycle("No schema drift detected. Draft written to ${draftFile.absolutePath}")
        } else {
            logger.lifecycle("Schema drift detected. Draft written to ${draftFile.absolutePath}\n${diff.summary()}")
        }
    }
}
