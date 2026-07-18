package cc.midolog.buildlogic.jpadsl

import java.io.File

class JpaDslRenderer {
    fun render(
        spec: JpaEntitySpec,
        properties: List<DomainProperty>,
        idProperty: DomainProperty,
        outputRoot: File,
    ) {
        val domainClass = spec.domainClass
        val domainPackage = domainClass.substringBeforeLast('.')
        val domainSimpleName = domainClass.simpleName()
        val contextPackage = domainPackage
            .removePrefix("cc.midolog.")
            .removeSuffix(".model")
        val generatedPackage = "cc.midolog.storage.jpa.$contextPackage"
        val generatedDir = File(outputRoot, generatedPackage.replace('.', '/'))
        generatedDir.mkdirs()

        val entityClassName = "${domainSimpleName}JpaEntity"
        val repositoryClassName = "${domainSimpleName}JpaRepository"
        val mapperClassName = "${domainSimpleName}JpaMapper"

        File(generatedDir, "$entityClassName.kt").writeText(
            renderEntity(generatedPackage, entityClassName, spec, properties, domainPackage),
        )
        File(generatedDir, "$repositoryClassName.kt").writeText(
            renderRepository(generatedPackage, repositoryClassName, entityClassName, idProperty.type),
        )
        File(generatedDir, "$mapperClassName.kt").writeText(
            renderMapper(generatedPackage, mapperClassName, entityClassName, spec, properties),
        )
    }

    private fun renderEntity(
        generatedPackage: String,
        entityClassName: String,
        spec: JpaEntitySpec,
        properties: List<DomainProperty>,
        domainPackage: String,
    ): String {
        val imports = linkedSetOf(
            "jakarta.persistence.Column",
            "jakarta.persistence.Entity",
            "jakarta.persistence.Id",
            "jakarta.persistence.Table",
        )
        properties.forEach { property ->
            val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
            if (fieldSpec.relation != null || spec.relations[property.name] != null) {
                return@forEach
            }
            if (fieldSpec.enumStrategy != null) {
                imports += "jakarta.persistence.EnumType"
                imports += "jakarta.persistence.Enumerated"
            }
            importableKotlinType(domainPackage, entityTypeFor(property, fieldSpec))?.let(imports::add)
        }
        spec.relations.values.forEach { relationSpec ->
            if (relationSpec.type == "manyToOne") {
                imports += "jakarta.persistence.FetchType"
                imports += "jakarta.persistence.JoinColumn"
                imports += "jakarta.persistence.ManyToOne"
            }
            if (relationSpec.type == "oneToMany") {
                imports += "jakarta.persistence.FetchType"
                imports += "jakarta.persistence.OneToMany"
            }
        }

        val entityProperties = properties
            .filter { property ->
                val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
                fieldSpec.relation == null && spec.relations[property.name] == null
            }
            .map { property -> renderEntityProperty(property, spec, spec.fields[property.name] ?: JpaFieldSpec()) }

        val relationProperties = spec.relations.map { (relationName, relationSpec) ->
            renderRelationEntityProperty(relationName, relationSpec)
        }

        return """package $generatedPackage

${imports.joinToString("\n") { "import $it" }}

@Entity
@Table(name = "${spec.table}")
class $entityClassName(
${(entityProperties + relationProperties).joinToString(",\n\n")}
)
"""
    }

    private fun renderEntityProperty(
        property: DomainProperty,
        spec: JpaEntitySpec,
        fieldSpec: JpaFieldSpec,
    ): String {
        val annotations = mutableListOf<String>()
        val columnName = fieldSpec.column ?: property.name
        val nullable = fieldSpec.nullable ?: property.type.isNullableKotlinType()
        if (property.name == spec.id) {
            annotations += "    @Id"
            annotations += "    @Column(name = \"$columnName\", nullable = false, updatable = false)"
        } else {
            if (fieldSpec.enumStrategy != null) {
                annotations += "    @Enumerated(EnumType.${fieldSpec.enumStrategy})"
            }
            annotations += "    @Column(name = \"$columnName\", nullable = $nullable)"
        }
        val entityType = entityTypeFor(property, fieldSpec)
        val defaultValue = defaultValueForProperty(entityType, fieldSpec)
        return annotations.joinToString("\n") + "\n    var ${property.name}: $entityType = $defaultValue"
    }

    private fun renderRelationEntityProperty(relationName: String, relationSpec: JpaRelationSpec): String {
        val targetEntityType = "${relationSpec.target.simpleName()}JpaEntity"
        return if (relationSpec.type == "manyToOne") {
            """    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "${relationSpec.joinColumn}", referencedColumnName = "${relationSpec.referencedColumn}", nullable = false)
    var $relationName: $targetEntityType = $targetEntityType()"""
        } else {
            """    @OneToMany(mappedBy = "${relationSpec.mappedBy}", fetch = FetchType.LAZY)
    var $relationName: MutableList<$targetEntityType> = mutableListOf()"""
        }
    }

    private fun renderRepository(
        generatedPackage: String,
        repositoryClassName: String,
        entityClassName: String,
        idType: String,
    ): String = """package $generatedPackage

import org.springframework.data.jpa.repository.JpaRepository

interface $repositoryClassName : JpaRepository<$entityClassName, $idType>
"""

    private fun renderMapper(
        generatedPackage: String,
        mapperClassName: String,
        entityClassName: String,
        spec: JpaEntitySpec,
        properties: List<DomainProperty>,
    ): String {
        val domainClass = spec.domainClass
        val domainSimpleName = domainClass.simpleName()
        val converterImports = properties
            .mapNotNull { spec.fields[it.name]?.converter }
            .distinct()
            .joinToString("") { "import $it\n" }
        val toEntityAssignments = (
            properties.mapNotNull { property ->
                val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
                val relationSpec = spec.relations[property.name]
                val expression = relationSpec?.let { toEntityRelationExpression(property, it) }
                    ?: toEntityExpression(property, fieldSpec)
                expression?.let { "            ${property.name} = $it" }
            } + spec.relations
                .filter { (relationName, relationSpec) ->
                    relationSpec.type == "manyToOne" && properties.none { it.name == relationName }
                }
                .map { (relationName, relationSpec) ->
                    "            $relationName = ${toEntityRelationExpression(DomainProperty(relationName, ""), relationSpec)}"
                }
            ).joinToString(",\n")

        val toDomainAssignments = properties.joinToString(",\n") { property ->
            val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
            val relationSpec = spec.relations[property.name]
            val expression = relationSpec?.let { toDomainRelationExpression(property, it) }
                ?: toDomainExpression(property, fieldSpec)
            "            ${property.name} = $expression"
        }

        return """package $generatedPackage

import $domainClass
$converterImports
object $mapperClassName {
    fun toEntity(domain: $domainSimpleName): $entityClassName =
        $entityClassName(
$toEntityAssignments
        )

    fun toDomain(entity: $entityClassName): $domainSimpleName =
        $domainSimpleName(
$toDomainAssignments
        )
}
"""
    }

    private fun toEntityExpression(property: DomainProperty, fieldSpec: JpaFieldSpec): String? {
        if (fieldSpec.converter != null) {
            return "${fieldSpec.converter!!.simpleName()}.toStorage(domain.${property.name})"
        }
        if (fieldSpec.relation != null) {
            return null
        }
        return "domain.${property.name}"
    }

    private fun toDomainExpression(property: DomainProperty, fieldSpec: JpaFieldSpec): String {
        if (fieldSpec.converter != null) {
            return "${fieldSpec.converter!!.simpleName()}.toDomain(entity.${property.name})"
        }
        if (fieldSpec.relation != null) {
            return "entity.${fieldSpec.relation}.id"
        }
        return "entity.${property.name}"
    }

    private fun toEntityRelationExpression(property: DomainProperty, relationSpec: JpaRelationSpec): String? {
        val targetEntityType = "${relationSpec.target.simpleName()}JpaEntity"
        if (relationSpec.type == "manyToOne") {
            return "$targetEntityType(id = domain.${relationSpec.sourceField})"
        }
        if (relationSpec.type == "oneToMany") {
            return "domain.${property.name}.map(${relationSpec.target.simpleName()}JpaMapper::toEntity).toMutableList()"
        }
        return null
    }

    private fun toDomainRelationExpression(property: DomainProperty, relationSpec: JpaRelationSpec): String {
        if (relationSpec.type == "manyToOne") {
            return "entity.${property.name}.id"
        }
        if (relationSpec.type == "oneToMany") {
            return relationSpec.toDomain ?: "emptyList()"
        }
        return "entity.${property.name}"
    }
}
