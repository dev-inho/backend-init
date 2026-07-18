package cc.midolog.buildlogic.jpadsl

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when the Flyway schema does not satisfy the typed JPA DSL, catching schema
 * drift statically (no database) — ahead of the `livePostgresTest` Hibernate `validate` check.
 * A green `verifyMigrationDraft` means the current migrations cover every table, column, and
 * foreign key the generated JPA mapping requires.
 */
abstract class VerifyMigrationDraftTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val domainSourceDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationSourceDir: DirectoryProperty

    @get:Input
    abstract val specsFingerprint: Property<String>

    @get:Internal
    var specsProvider: (() -> List<JpaEntitySpec>)? = null

    init {
        group = "verification"
        description = "Fails if the Flyway schema does not satisfy the JPA DSL expected schema."
    }

    /** Computes drift and throws when it is non-empty. */
    @TaskAction
    fun verify() {
        val specs = specsProvider?.invoke().orEmpty()
        if (specs.isEmpty()) {
            throw GradleException("jpaDsl must declare at least one entity")
        }
        val diff = MigrationDraftEngine.computeDiff(
            specs,
            domainSourceDir.get().asFile,
            migrationSourceDir.get().asFile,
        )
        if (!diff.isEmpty) {
            throw GradleException(
                "Flyway schema does not match the JPA DSL. Run generateMigrationDraft and update the migration.\n" +
                    diff.summary(),
            )
        }
        logger.lifecycle("verifyMigrationDraft: no schema drift detected.")
    }
}
