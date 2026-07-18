package cc.midolog.buildlogic.jpadsl

/**
 * Schema models used by the migration-draft generator.
 *
 * "Expected" is derived from the typed JPA DSL (the source of truth for what the
 * generated JPA mapping requires). "Actual" is parsed from the current Flyway
 * migration SQL. A [SchemaDiff] is the missing/mismatched delta between them.
 *
 * The generator never mutates schema; it only reports drift and renders draft SQL.
 */

data class ExpectedColumn(
    val name: String,
    val sqlType: String,
    val nullable: Boolean,
    val primaryKey: Boolean,
)

data class ExpectedForeignKey(
    val column: String,
    val referencedTable: String,
    val referencedColumn: String,
)

data class ExpectedTable(
    val name: String,
    val columns: List<ExpectedColumn>,
    val foreignKeys: List<ExpectedForeignKey>,
)

data class ExpectedSchema(
    val tables: List<ExpectedTable>,
)

data class ActualColumn(
    val name: String,
    val sqlType: String,
    val nullable: Boolean,
    val primaryKey: Boolean,
)

data class ActualForeignKey(
    val column: String,
    val referencedTable: String,
    val referencedColumn: String,
)

data class ActualTable(
    val name: String,
    val columns: List<ActualColumn>,
    val foreignKeys: List<ActualForeignKey>,
)

data class ActualSchema(
    val tables: List<ActualTable>,
) {
    private val byName: Map<String, ActualTable> = tables.associateBy { it.name.lowercase() }

    fun table(name: String): ActualTable? = byName[name.lowercase()]
}

/** A column present in both schemas whose type or nullability disagrees. */
data class ColumnMismatch(
    val table: String,
    val expected: ExpectedColumn,
    val actual: ActualColumn,
    val reason: String,
)

data class SchemaDiff(
    val missingTables: List<ExpectedTable>,
    val missingColumns: List<Pair<ExpectedTable, ExpectedColumn>>,
    val missingForeignKeys: List<Pair<ExpectedTable, ExpectedForeignKey>>,
    val mismatches: List<ColumnMismatch>,
) {
    val isEmpty: Boolean
        get() = missingTables.isEmpty() &&
            missingColumns.isEmpty() &&
            missingForeignKeys.isEmpty() &&
            mismatches.isEmpty()

    /** Human-readable summary for verify failures and task logs. */
    fun summary(): String = buildString {
        if (isEmpty) {
            append("No schema drift detected.")
            return@buildString
        }
        missingTables.forEach { append("missing table: ${it.name}\n") }
        missingColumns.forEach { (t, c) -> append("missing column: ${t.name}.${c.name} (${c.sqlType})\n") }
        missingForeignKeys.forEach { (t, fk) ->
            append("missing foreign key: ${t.name}.${fk.column} -> ${fk.referencedTable}(${fk.referencedColumn})\n")
        }
        mismatches.forEach { append("mismatch: ${it.table}.${it.expected.name} — ${it.reason}\n") }
    }.trimEnd()
}
