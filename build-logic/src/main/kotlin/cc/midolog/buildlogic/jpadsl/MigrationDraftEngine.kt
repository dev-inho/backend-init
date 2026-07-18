package cc.midolog.buildlogic.jpadsl

import java.io.File

/**
 * Shared pipeline behind [GenerateMigrationDraftTask] and [VerifyMigrationDraftTask]:
 * build the expected schema from the DSL, parse the current Flyway schema, and diff them.
 */
object MigrationDraftEngine {
    private val VERSIONED = Regex("V(\\d+)__.*\\.sql", RegexOption.IGNORE_CASE)

    /** Computes the drift between the DSL [specs] and the Flyway migrations in [migrationDir]. */
    fun computeDiff(specs: List<JpaEntitySpec>, domainRoot: File, migrationDir: File): SchemaDiff {
        val expected = ExpectedSchemaBuilder().build(specs, domainRoot)
        val actual = FlywaySchemaParser().parse(migrationFiles(migrationDir))
        return SchemaDiffer.diff(expected, actual)
    }

    /** Returns the versioned migration files in [migrationDir], ordered by version. */
    fun migrationFiles(migrationDir: File): List<File> =
        (migrationDir.listFiles()?.toList() ?: emptyList())
            .filter { it.isFile && VERSIONED.matches(it.name) }
            .sortedBy { versionOf(it.name) }

    /** The version number for the next migration after those in [files]. */
    fun nextVersion(files: List<File>): Int = (files.maxOfOrNull { versionOf(it.name) } ?: 0) + 1

    private fun versionOf(name: String): Int =
        VERSIONED.find(name)?.groupValues?.get(1)?.toInt() ?: 0
}
