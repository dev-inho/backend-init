package cc.midolog.buildlogic.jpadsl

import org.gradle.api.GradleException

/** Whether a column is a primary key, a foreign key, or a plain scalar column. */
enum class ColumnRole { PRIMARY_KEY, FOREIGN_KEY, REGULAR }

/**
 * Fixed Kotlin-storage-type to PostgreSQL-type mapping for the migration-draft generator.
 *
 * The mapping is intentionally explicit and matches the hand-written `V1` baseline:
 * identifier columns (primary and foreign keys) are `VARCHAR(64)`, other strings and
 * `STRING`-strategy enums are `VARCHAR(255)`. Unknown types fail fast so a human adds an
 * explicit mapping (or a `storageType` override) instead of the generator guessing.
 */
object SqlTypeMapper {
    /**
     * Resolves the SQL column type for a Kotlin [kotlinType] given its [role] and optional
     * [enumStrategy]. `STRING`-strategy enums always map to `VARCHAR(255)` regardless of the
     * underlying enum class name.
     */
    fun sqlType(kotlinType: String, role: ColumnRole, enumStrategy: String?): String {
        if (enumStrategy == "STRING") {
            return "VARCHAR(255)"
        }
        return when (val base = kotlinType.removeSuffix("?")) {
            "String" ->
                if (role == ColumnRole.PRIMARY_KEY || role == ColumnRole.FOREIGN_KEY) "VARCHAR(64)" else "VARCHAR(255)"
            "Int", "Integer" -> "INTEGER"
            "Long" -> "BIGINT"
            "Short" -> "SMALLINT"
            "Boolean" -> "BOOLEAN"
            "Double" -> "DOUBLE PRECISION"
            "Float" -> "REAL"
            "BigDecimal", "java.math.BigDecimal" -> "NUMERIC"
            "UUID", "java.util.UUID" -> "UUID"
            "LocalDate", "java.time.LocalDate" -> "DATE"
            "Instant", "LocalDateTime", "OffsetDateTime",
            "java.time.Instant", "java.time.LocalDateTime", "java.time.OffsetDateTime",
            -> "TIMESTAMP"
            else -> throw GradleException(
                "No SQL type mapping for Kotlin type '$base' (role=$role). " +
                    "Add an explicit mapping in SqlTypeMapper or a storageType override in the jpaDsl field.",
            )
        }
    }
}
