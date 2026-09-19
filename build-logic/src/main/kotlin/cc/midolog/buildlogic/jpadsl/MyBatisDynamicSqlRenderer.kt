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

        if (domainClass.contains(".customers.")) {
            val mapperClassName = "${domainSimpleName}Mapper"
            val adapterClassName = "${domainSimpleName}MyBatisCustomerRepositoryAdapter"
            val autoConfigClassName = "${domainSimpleName}MyBatisAutoConfiguration"
            val idProperty = properties.firstOrNull { it.name == spec.id } ?: properties.first()

            File(generatedDir, "$mapperClassName.kt").writeText(
                renderMapper(generatedPackage, mapperClassName),
            )
            File(generatedDir, "$adapterClassName.kt").writeText(
                renderCustomerAdapter(generatedPackage, adapterClassName, domainClass, domainSimpleName, mapperClassName, supportClassName, spec, properties, idProperty),
            )
            File(generatedDir, "$autoConfigClassName.kt").writeText(
                renderCustomerAutoConfiguration(generatedPackage, autoConfigClassName, domainClass, domainSimpleName, mapperClassName, adapterClassName, idProperty),
            )
        }
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

    private fun renderMapper(generatedPackage: String, mapperClassName: String): String = """package $generatedPackage

import org.apache.ibatis.annotations.Mapper
import org.mybatis.dynamic.sql.util.mybatis3.CommonCountMapper
import org.mybatis.dynamic.sql.util.mybatis3.CommonDeleteMapper
import org.mybatis.dynamic.sql.util.mybatis3.CommonGeneralInsertMapper
import org.mybatis.dynamic.sql.util.mybatis3.CommonSelectMapper
import org.mybatis.dynamic.sql.util.mybatis3.CommonUpdateMapper

@Mapper
interface $mapperClassName : CommonSelectMapper, CommonGeneralInsertMapper, CommonUpdateMapper, CommonDeleteMapper, CommonCountMapper
"""

    private fun renderCustomerAdapter(
        generatedPackage: String,
        adapterClassName: String,
        domainClass: String,
        domainSimpleName: String,
        mapperClassName: String,
        supportClassName: String,
        spec: JpaEntitySpec,
        properties: List<DomainProperty>,
        idProperty: DomainProperty,
    ): String {
        val instanceValName = domainSimpleName.replaceFirstChar { it.lowercase() }
        val nonIdProperties = properties.filter { it.name != idProperty.name }

        val propertyMappingExpressions = properties.joinToString(",\n") { prop ->
            val colName = spec.fields[prop.name]?.column ?: prop.name
            val typeStr = prop.type
            val expr = when (typeStr) {
                "String" -> "(row[\"$colName\"] ?: row[\"${colName.uppercase()}\"]) as String"
                "String?" -> "(row[\"$colName\"] ?: row[\"${colName.uppercase()}\"]) as String?"
                "Long" -> "((row[\"$colName\"] ?: row[\"${colName.uppercase()}\"]) as Number).toLong()"
                "Long?" -> "((row[\"$colName\"] ?: row[\"${colName.uppercase()}\"]) as? Number)?.toLong()"
                "Int" -> "((row[\"$colName\"] ?: row[\"${colName.uppercase()}\"]) as Number).toInt()"
                "Int?" -> "((row[\"$colName\"] ?: row[\"${colName.uppercase()}\"]) as? Number)?.toInt()"
                "Boolean" -> "(row[\"$colName\"] ?: row[\"${colName.uppercase()}\"]) as Boolean"
                "Boolean?" -> "(row[\"$colName\"] ?: row[\"${colName.uppercase()}\"]) as Boolean?"
                "java.time.Instant" -> """when (val v = row["$colName"] ?: row["${colName.uppercase()}"]) {
            is java.time.Instant -> v
            is java.sql.Timestamp -> v.toInstant()
            else -> java.time.Instant.parse(v.toString())
        }"""
                "java.time.Instant?" -> """when (val v = row["$colName"] ?: row["${colName.uppercase()}"]) {
            null -> null
            is java.time.Instant -> v
            is java.sql.Timestamp -> v.toInstant()
            else -> java.time.Instant.parse(v.toString())
        }"""
                else -> "(row[\"$colName\"] ?: row[\"${colName.uppercase()}\"]) as $typeStr"
            }
            "            ${prop.name} = $expr"
        }

        val updateSetStatements = nonIdProperties.joinToString("\n            ") { prop ->
            "set($supportClassName.${prop.name}).equalTo(entity.${prop.name})"
        }

        val insertSetStatements = properties.joinToString("\n            ") { prop ->
            "set($supportClassName.${prop.name}).toValue(entity.${prop.name})"
        }

        val selectColumns = properties.joinToString(",\n            ") { "$supportClassName.${it.name}" }

        return """package $generatedPackage

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import $domainClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.countFrom
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.deleteFrom
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.insertInto
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.select
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.update

class $adapterClassName(
    private val mapper: $mapperClassName,
) : CustomerRepositoryPort<$domainSimpleName, ${idProperty.type}> {

    override suspend fun findById(id: ${idProperty.type}): $domainSimpleName? = withContext(Dispatchers.IO) {
        val selectStatement = select(
            $selectColumns
        ) {
            from($supportClassName.$instanceValName)
            where { $supportClassName.${idProperty.name} isEqualTo id }
        }
        val row = mapper.selectOneMappedRow(selectStatement) ?: return@withContext null
        $domainSimpleName(
$propertyMappingExpressions
        )
    }

    override suspend fun save(entity: $domainSimpleName): $domainSimpleName = withContext(Dispatchers.IO) {
        val exists = countFrom(mapper::count, $supportClassName.$instanceValName) {
            where { $supportClassName.${idProperty.name} isEqualTo entity.${idProperty.name} }
        } > 0
        if (exists) {
            update(mapper::update, $supportClassName.$instanceValName) {
                $updateSetStatements
                where { $supportClassName.${idProperty.name} isEqualTo entity.${idProperty.name} }
            }
        } else {
            insertInto(mapper::generalInsert, $supportClassName.$instanceValName) {
                $insertSetStatements
            }
        }
        entity
    }

    override suspend fun deleteById(id: ${idProperty.type}): Boolean = withContext(Dispatchers.IO) {
        val affected = deleteFrom(mapper::delete, $supportClassName.$instanceValName) {
            where { $supportClassName.${idProperty.name} isEqualTo id }
        }
        affected > 0
    }
}
"""
    }

    private fun renderCustomerAutoConfiguration(
        generatedPackage: String,
        autoConfigClassName: String,
        domainClass: String,
        domainSimpleName: String,
        mapperClassName: String,
        adapterClassName: String,
        idProperty: DomainProperty,
    ): String {
        val instanceValName = domainSimpleName.replaceFirstChar { it.lowercase() }
        return """package $generatedPackage

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import $domainClass
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnProperty(prefix = "storage.persistence", name = ["provider"], havingValue = "mybatis")
class $autoConfigClassName {

    @Bean
    @ConditionalOnClass($domainSimpleName::class)
    @ConditionalOnMissingBean(name = ["${instanceValName}CustomerRepositoryPort"])
    fun ${instanceValName}CustomerRepositoryPort(
        mapper: $mapperClassName,
    ): CustomerRepositoryPort<$domainSimpleName, ${idProperty.type}> {
        return $adapterClassName(mapper)
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
