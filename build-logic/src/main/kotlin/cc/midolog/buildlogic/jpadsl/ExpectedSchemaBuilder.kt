package cc.midolog.buildlogic.jpadsl

import java.io.File

/**
 * Derives the [ExpectedSchema] a set of [JpaEntitySpec]s requires, reusing the same
 * domain parsing, validation, and type-resolution rules as the JPA source generator so
 * the expected schema can never drift from the generated entities.
 *
 * Column rules mirror [JpaDslRenderer]:
 * - scalar column name = `JpaFieldSpec.column ?: property.name`
 * - nullability = `JpaFieldSpec.nullable ?: property.type` nullability (primary key is never null)
 * - storage type = `entityTypeFor(property, fieldSpec)`
 * - a relation-backing property (`JpaFieldSpec.relation != null`) is not its own column;
 *   the physical column comes from the owning `manyToOne` relation's `joinColumn`.
 */
class ExpectedSchemaBuilder(
    private val parser: DomainSourceParser = DomainSourceParser(),
    private val validator: JpaDslValidator = JpaDslValidator(),
) {
    private class ParsedSpec(
        val spec: JpaEntitySpec,
        val properties: List<DomainProperty>,
        val idProperty: DomainProperty,
    )

    /** Builds the expected schema for [specs], resolving domain sources under [domainRoot]. */
    fun build(specs: List<JpaEntitySpec>, domainRoot: File): ExpectedSchema {
        val parsed = specs.map { spec ->
            val domainFile = File(domainRoot, spec.domainClass.replace('.', '/') + ".kt")
            val properties = parser.parse(spec.domainClass, domainFile)
            val idProperty = validator.validate(spec.domainClass, properties, spec)
            ParsedSpec(spec, properties, idProperty)
        }
        val byClass = parsed.associateBy { it.spec.domainClass }

        // Pass 1: scalar columns per table (primary key + regular columns).
        val scalarTables = parsed.associate { it.spec.domainClass to scalarColumns(it) }

        // Pass 2: foreign-key columns, typed to match the referenced table's column.
        return ExpectedSchema(
            parsed.map { parsedSpec ->
                val scalar = scalarTables.getValue(parsedSpec.spec.domainClass)
                val (fkColumns, foreignKeys) = foreignKeys(parsedSpec, byClass, scalarTables)
                ExpectedTable(
                    name = parsedSpec.spec.table,
                    columns = scalar + fkColumns,
                    foreignKeys = foreignKeys,
                )
            },
        )
    }

    private fun scalarColumns(parsed: ParsedSpec): List<ExpectedColumn> {
        val spec = parsed.spec
        return parsed.properties.mapNotNull { property ->
            val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
            // Relation-backing fields and relation-named properties are not scalar columns.
            if (fieldSpec.relation != null || spec.relations[property.name] != null) {
                return@mapNotNull null
            }
            val isPrimaryKey = property.name == spec.id
            val role = if (isPrimaryKey) ColumnRole.PRIMARY_KEY else ColumnRole.REGULAR
            val storageType = entityTypeFor(property, fieldSpec)
            val nullable = if (isPrimaryKey) false else (fieldSpec.nullable ?: property.type.isNullableKotlinType())
            ExpectedColumn(
                name = fieldSpec.column ?: property.name,
                sqlType = SqlTypeMapper.sqlType(storageType, role, fieldSpec.enumStrategy),
                nullable = nullable,
                primaryKey = isPrimaryKey,
            )
        }
    }

    private fun foreignKeys(
        parsed: ParsedSpec,
        byClass: Map<String, ParsedSpec>,
        scalarTables: Map<String, List<ExpectedColumn>>,
    ): Pair<List<ExpectedColumn>, List<ExpectedForeignKey>> {
        val columns = mutableListOf<ExpectedColumn>()
        val foreignKeys = mutableListOf<ExpectedForeignKey>()
        parsed.spec.relations.values
            .filter { it.type == "manyToOne" }
            .forEach { relation ->
                val target = byClass[relation.target]
                    ?: error("Relation target '${relation.target}' is not a declared entity")
                val referencedColumn = relation.referencedColumn!!
                val referencedType = scalarTables.getValue(relation.target)
                    .firstOrNull { it.name == referencedColumn }?.sqlType
                    ?: SqlTypeMapper.sqlType(target.idProperty.type, ColumnRole.FOREIGN_KEY, null)
                columns += ExpectedColumn(
                    name = relation.joinColumn!!,
                    sqlType = referencedType,
                    nullable = false,
                    primaryKey = false,
                )
                foreignKeys += ExpectedForeignKey(
                    column = relation.joinColumn!!,
                    referencedTable = target.spec.table,
                    referencedColumn = referencedColumn,
                )
            }
        return columns to foreignKeys
    }
}
