package cc.midolog.buildlogic.jpadsl

import org.gradle.api.GradleException

class JpaDslValidator {
    fun validate(domainClass: String, properties: List<DomainProperty>, spec: JpaEntitySpec): DomainProperty {
        if (spec.table.isBlank()) {
            throw GradleException("Entity spec for $domainClass must declare table")
        }

        val idProperty = properties.find { it.name == spec.id }
            ?: throw GradleException("ID property '${spec.id}' does not exist in $domainClass")

        spec.fields.keys.forEach { fieldName ->
            if (properties.none { it.name == fieldName }) {
                throw GradleException("Unknown field '$fieldName' for $domainClass")
            }
        }

        spec.relations.forEach { (relationName, relationSpec) ->
            validateRelation(domainClass, properties, relationName, relationSpec)
        }

        properties.forEach { property ->
            val fieldSpec = spec.fields[property.name] ?: JpaFieldSpec()
            if (fieldSpec.converter != null) {
                if (fieldSpec.storageType == null) {
                    throw GradleException(
                        "Field '${property.name}' in $domainClass declares converter without storageType",
                    )
                }
                if (fieldSpec.converter.isNullOrBlank()) {
                    throw GradleException("Field '${property.name}' in $domainClass declares invalid converter")
                }
            }
            if (fieldSpec.enumStrategy != null && fieldSpec.enumStrategy !in setOf("STRING")) {
                throw GradleException(
                    "Field '${property.name}' in $domainClass has unsupported enum strategy '${fieldSpec.enumStrategy}'",
                )
            }
            if (fieldSpec.relation != null && spec.relations[fieldSpec.relation] == null) {
                throw GradleException(
                    "Field '${property.name}' in $domainClass points to unknown relation '${fieldSpec.relation}'",
                )
            }
        }

        return idProperty
    }

    private fun validateRelation(
        domainClass: String,
        properties: List<DomainProperty>,
        relationName: String,
        relationSpec: JpaRelationSpec,
    ) {
        if (relationSpec.type !in setOf("manyToOne", "oneToMany")) {
            throw GradleException(
                "Relation '$relationName' in $domainClass has unsupported relation type '${relationSpec.type}'",
            )
        }
        if (relationSpec.target.isBlank()) {
            throw GradleException("Relation '$relationName' in $domainClass must declare target domain class")
        }
        if (relationSpec.target == domainClass) {
            throw GradleException("Relation '$relationName' in $domainClass cannot target itself")
        }
        if (relationSpec.type == "manyToOne") {
            if (relationSpec.sourceField.isNullOrBlank() ||
                properties.none { it.name == relationSpec.sourceField }
            ) {
                throw GradleException("Relation '$relationName' in $domainClass must declare existing sourceField")
            }
            if (relationSpec.joinColumn.isNullOrBlank()) {
                throw GradleException("Relation '$relationName' in $domainClass must declare joinColumn")
            }
            if (relationSpec.referencedColumn.isNullOrBlank()) {
                throw GradleException("Relation '$relationName' in $domainClass must declare referencedColumn")
            }
        }
        if (relationSpec.type == "oneToMany" && relationSpec.mappedBy.isNullOrBlank()) {
            throw GradleException("Relation '$relationName' in $domainClass must declare mappedBy")
        }
    }
}
