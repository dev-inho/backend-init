package cc.midolog.buildlogic.jpadsl

import java.io.File

class MyBatisDynamicSqlRenderer {
    fun render(
        spec: JpaEntitySpec,
        properties: List<DomainProperty>,
        outputRoot: File,
    ) {
        val domainClass = spec.domainClass
        val domainPackage = domainClass.substringBeforeLast('.')
        val domainSimpleName = domainClass.simpleName()
        val contextPackage = domainPackage
            .removePrefix("cc.midolog.")
            .removeSuffix(".model")
        val generatedPackage = "cc.midolog.storage.mybatis.$contextPackage"
        val generatedDir = File(outputRoot, generatedPackage.replace('.', '/'))
        generatedDir.mkdirs()

        val supportClassName = "${domainSimpleName}DynamicSqlSupport"
        File(generatedDir, "$supportClassName.kt").writeText(
            renderSupport(generatedPackage, supportClassName, domainSimpleName, spec, properties, domainPackage),
        )
    }

    private fun renderSupport(
        generatedPackage: String,
        supportClassName: String,
        domainSimpleName: String,
        spec: JpaEntitySpec,
        properties: List<DomainProperty>,
        domainPackage: String,
    ): String {
        val instanceValName = domainSimpleName.replaceFirstChar { it.lowercase() }
        val imports = linkedSetOf(
            "java.sql.JDBCType",
            "org.mybatis.dynamic.sql.SqlColumn",
            "org.mybatis.dynamic.sql.SqlTable",
        )

        val persistentProperties = properties.filter { property ->
            val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
            fieldSpec.relation == null && spec.relations[property.name] == null
        }

        persistentProperties.forEach { property ->
            val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
            importableKotlinType(domainPackage, property.type.removeSuffix("?"))?.let(imports::add)
        }

        val topLevelColumns = persistentProperties.map { property ->
            val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
            val isNullable = fieldSpec.nullable ?: property.type.endsWith("?")
            val rawType = property.type.removeSuffix("?").simpleName()
            val columnType = if (isNullable) "$rawType?" else rawType
            "    val ${property.name}: SqlColumn<$columnType> = $instanceValName.${property.name}"
        }

        val tableColumns = persistentProperties.map { property ->
            val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
            val columnName = fieldSpec.column ?: property.name
            val isNullable = fieldSpec.nullable ?: property.type.endsWith("?")
            val rawType = property.type.removeSuffix("?").simpleName()
            val columnType = if (isNullable) "$rawType?" else rawType
            val jdbcType = jdbcTypeFor(property.type, fieldSpec)
            "        val ${property.name}: SqlColumn<$columnType> = column(\"$columnName\", JDBCType.$jdbcType)"
        }

        return """package $generatedPackage

${imports.joinToString("\n") { "import $it" }}

object $supportClassName {
    val $instanceValName = $domainSimpleName()

${topLevelColumns.joinToString("\n")}

    class $domainSimpleName : SqlTable("${spec.table}") {
${tableColumns.joinToString("\n")}
    }
}
"""
    }

    private fun jdbcTypeFor(type: String, fieldSpec: JpaFieldSpec): String {
        val baseType = type.removeSuffix("?")
        if (fieldSpec.enumStrategy != null) {
            return "VARCHAR"
        }
        return when (baseType) {
            "String" -> "VARCHAR"
            "Long" -> "BIGINT"
            "Int" -> "INTEGER"
            "Short" -> "SMALLINT"
            "Byte" -> "TINYINT"
            "Double" -> "DOUBLE"
            "Float" -> "REAL"
            "Boolean" -> "BOOLEAN"
            "java.time.Instant", "Instant" -> "TIMESTAMP"
            "java.time.LocalDate", "LocalDate" -> "DATE"
            "java.time.LocalDateTime", "LocalDateTime" -> "TIMESTAMP"
            "java.math.BigDecimal", "BigDecimal" -> "DECIMAL"
            else -> "VARCHAR"
        }
    }
}
