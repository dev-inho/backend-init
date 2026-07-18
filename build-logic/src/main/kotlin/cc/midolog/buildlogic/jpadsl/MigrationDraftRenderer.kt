package cc.midolog.buildlogic.jpadsl

/**
 * Renders a [SchemaDiff] into draft migration SQL for human review.
 *
 * Only additive statements are emitted: `CREATE TABLE`, `ALTER TABLE ADD COLUMN`, and
 * `ALTER TABLE ADD CONSTRAINT ... FOREIGN KEY`. Adding a `NOT NULL` column to an existing
 * table and every [ColumnMismatch] are annotated as `-- WARNING` comments, because they may
 * require a default/backfill or are potentially destructive — the generator never emits
 * `DROP`/`ALTER TYPE` automatically.
 */
class MigrationDraftRenderer {
    /** Renders [diff] as SQL. When [diff] is empty, returns only a no-drift header comment. */
    fun render(diff: SchemaDiff): String {
        val header = buildString {
            append("-- Generated migration draft — review before applying.\n")
            append("-- Source: jpaDsl expected schema vs current Flyway schema.\n")
            append("-- Additive statements only; destructive changes are reported as warnings, not generated.\n")
        }
        if (diff.isEmpty) {
            return header + "\n-- No schema drift detected.\n"
        }

        val blocks = mutableListOf<String>()
        diff.missingTables.forEach { blocks += renderCreateTable(it) }
        diff.missingColumns.forEach { (table, column) -> blocks += renderAddColumn(table.name, column) }
        diff.missingForeignKeys.forEach { (table, fk) -> blocks += renderAddForeignKey(table.name, fk) }
        diff.mismatches.forEach { blocks += renderMismatchWarning(it) }

        return header + "\n" + blocks.joinToString("\n\n") + "\n"
    }

    private fun renderCreateTable(table: ExpectedTable): String {
        val columnLines = table.columns.map { column ->
            "    " + columnDefinition(column)
        }
        val constraintLines = table.foreignKeys.map { fk ->
            "    CONSTRAINT ${foreignKeyName(table.name, fk.column)} " +
                "FOREIGN KEY (${fk.column}) REFERENCES ${fk.referencedTable} (${fk.referencedColumn})"
        }
        val body = (columnLines + constraintLines).joinToString(",\n")
        return "CREATE TABLE IF NOT EXISTS ${table.name} (\n$body\n);"
    }

    private fun renderAddColumn(table: String, column: ExpectedColumn): String {
        val statement = "ALTER TABLE $table ADD COLUMN ${columnDefinition(column)};"
        return if (!column.nullable) {
            "-- WARNING: adding NOT NULL column to existing table '$table' — provide a default or backfill first.\n$statement"
        } else {
            statement
        }
    }

    private fun renderAddForeignKey(table: String, fk: ExpectedForeignKey): String =
        "ALTER TABLE $table ADD CONSTRAINT ${foreignKeyName(table, fk.column)} " +
            "FOREIGN KEY (${fk.column}) REFERENCES ${fk.referencedTable} (${fk.referencedColumn});"

    private fun renderMismatchWarning(mismatch: ColumnMismatch): String =
        "-- WARNING: manual review required — column ${mismatch.table}.${mismatch.expected.name} " +
            "differs (${mismatch.reason}). Not auto-generated (potentially destructive)."

    private fun columnDefinition(column: ExpectedColumn): String = buildString {
        append(column.name).append(' ').append(column.sqlType)
        if (!column.nullable && !column.primaryKey) append(" NOT NULL")
        if (column.primaryKey) append(" PRIMARY KEY")
    }

    private fun foreignKeyName(table: String, column: String): String = "fk_${table}_$column"
}
