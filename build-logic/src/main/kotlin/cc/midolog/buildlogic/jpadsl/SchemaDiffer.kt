package cc.midolog.buildlogic.jpadsl

/**
 * Computes what the [ExpectedSchema] requires that the [ActualSchema] does not yet provide.
 *
 * The diff is directional: only expected-but-missing elements are drift, because a missing
 * table/column/FK is exactly what makes Hibernate `ddl-auto=validate` (and runtime boot)
 * fail. Extra elements in the actual schema (indexes, seed rows, spare columns) are not drift.
 * Type/nullability disagreements on an existing column are reported as [ColumnMismatch]es and
 * are never auto-migrated, since narrowing a type or tightening nullability can be destructive.
 */
object SchemaDiffer {
    /** Diffs [expected] against [actual]. */
    fun diff(expected: ExpectedSchema, actual: ActualSchema): SchemaDiff {
        val missingTables = mutableListOf<ExpectedTable>()
        val missingColumns = mutableListOf<Pair<ExpectedTable, ExpectedColumn>>()
        val missingForeignKeys = mutableListOf<Pair<ExpectedTable, ExpectedForeignKey>>()
        val mismatches = mutableListOf<ColumnMismatch>()

        expected.tables.forEach { table ->
            val actualTable = actual.table(table.name)
            if (actualTable == null) {
                missingTables += table
                return@forEach
            }
            val actualColumns = actualTable.columns.associateBy { it.name.lowercase() }
            table.columns.forEach { column ->
                val actualColumn = actualColumns[column.name.lowercase()]
                when {
                    actualColumn == null -> missingColumns += table to column
                    else -> columnMismatch(table.name, column, actualColumn)?.let { mismatches += it }
                }
            }
            table.foreignKeys.forEach { fk ->
                val present = actualTable.foreignKeys.any {
                    it.column.equals(fk.column, ignoreCase = true) &&
                        it.referencedTable.equals(fk.referencedTable, ignoreCase = true) &&
                        it.referencedColumn.equals(fk.referencedColumn, ignoreCase = true)
                }
                if (!present) missingForeignKeys += table to fk
            }
        }

        return SchemaDiff(missingTables, missingColumns, missingForeignKeys, mismatches)
    }

    private fun columnMismatch(table: String, expected: ExpectedColumn, actual: ActualColumn): ColumnMismatch? {
        val typeDiffers = normalizeType(expected.sqlType) != normalizeType(actual.sqlType)
        val nullabilityDiffers = expected.nullable != actual.nullable
        if (!typeDiffers && !nullabilityDiffers) return null
        val reasons = buildList {
            if (typeDiffers) add("type ${expected.sqlType} vs ${actual.sqlType}")
            if (nullabilityDiffers) {
                add("nullability ${expected.nullable.asNull()} vs ${actual.nullable.asNull()}")
            }
        }
        return ColumnMismatch(table, expected, actual, reasons.joinToString("; "))
    }

    private fun Boolean.asNull(): String = if (this) "NULL" else "NOT NULL"

    /** Normalises a SQL type for comparison: upper-cased with insignificant spaces removed. */
    private fun normalizeType(type: String): String = type.uppercase().replace(Regex("\\s*\\(\\s*"), "(")
        .replace(Regex("\\s*\\)\\s*"), ")")
        .replace(Regex("\\s+"), " ")
        .trim()
}
