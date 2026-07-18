package cc.midolog.buildlogic.jpadsl

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

abstract class ValidateJpaDslGeneratorNegativeCasesTask : DefaultTask() {
    init {
        group = "verification"
        description = "Validates representative negative cases for the typed JPA DSL generator."
    }

    @TaskAction
    fun validate() {
        val sampleClass = "cc.midolog.sample.model.Sample"
        val relationParent = "cc.midolog.sample.model.RelationParent"
        val baseProperties = listOf(
            DomainProperty("id", "String"),
            DomainProperty("name", "String"),
            DomainProperty("parentId", "String"),
        )
        val parser = DomainSourceParser()
        val validator = JpaDslValidator()
        val tmpDomain = File(temporaryDir, "domain").also { it.mkdirs() }

        expectFailure("domain source missing", "Domain source not found for $sampleClass") {
            parser.parse(sampleClass, File(tmpDomain, "Missing.kt"))
        }

        val sampleFile = File(tmpDomain, "Sample.kt")
        sampleFile.writeText("package cc.midolog.sample.model\nclass Sample\n")
        expectFailure("non data class", "Only primary-constructor data classes are supported: $sampleClass") {
            parser.parse(sampleClass, sampleFile)
        }

        sampleFile.writeText("package cc.midolog.sample.model\ndata class Sample(\n    id: String,\n)\n")
        expectFailure("unsupported constructor property", "Unsupported constructor property in $sampleClass: id: String") {
            parser.parse(sampleClass, sampleFile)
        }

        expectFailure("missing id", "ID property 'missing' does not exist in $sampleClass") {
            validator.validate(sampleClass, baseProperties, entity(sampleClass, id = "missing"))
        }
        expectFailure("unknown field", "Unknown field 'ghost' for $sampleClass") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply { fields["ghost"] = JpaFieldSpec() },
            )
        }
        expectFailure("converter without storageType", "Field 'name' in $sampleClass declares converter without storageType") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply { fields["name"] = JpaFieldSpec().apply { converter = "ExampleConverter" } },
            )
        }
        expectFailure("invalid converter", "Field 'name' in $sampleClass declares invalid converter") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply {
                    fields["name"] = JpaFieldSpec().apply {
                        storageType = "String"
                        converter = ""
                    }
                },
            )
        }
        expectFailure("unsupported enum", "Field 'name' in $sampleClass has unsupported enum strategy 'ORDINAL'") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply { fields["name"] = JpaFieldSpec().apply { enumStrategy = "ORDINAL" } },
            )
        }
        expectFailure("unknown relation", "Field 'parentId' in $sampleClass points to unknown relation 'parent'") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply { fields["parentId"] = JpaFieldSpec().apply { relation = "parent" } },
            )
        }
        expectFailure("unsupported relation type", "Relation 'parent' in $sampleClass has unsupported relation type 'manyToMany'") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply {
                    relations["parent"] = JpaRelationSpec().apply {
                        type = "manyToMany"
                        target = relationParent
                    }
                },
            )
        }
        expectFailure("missing target", "Relation 'parent' in $sampleClass must declare target domain class") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply { relations["parent"] = JpaRelationSpec().apply { type = "manyToOne" } },
            )
        }
        expectFailure("self target", "Relation 'parent' in $sampleClass cannot target itself") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply {
                    relations["parent"] = JpaRelationSpec().apply {
                        type = "manyToOne"
                        target = sampleClass
                    }
                },
            )
        }
        expectFailure("missing sourceField", "Relation 'parent' in $sampleClass must declare existing sourceField") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply {
                    relations["parent"] = JpaRelationSpec().apply {
                        type = "manyToOne"
                        target = relationParent
                        sourceField = "missing"
                    }
                },
            )
        }
        expectFailure("missing joinColumn", "Relation 'parent' in $sampleClass must declare joinColumn") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply {
                    relations["parent"] = JpaRelationSpec().apply {
                        type = "manyToOne"
                        target = relationParent
                        sourceField = "parentId"
                        referencedColumn = "id"
                    }
                },
            )
        }
        expectFailure("missing referencedColumn", "Relation 'parent' in $sampleClass must declare referencedColumn") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply {
                    relations["parent"] = JpaRelationSpec().apply {
                        type = "manyToOne"
                        target = relationParent
                        sourceField = "parentId"
                        joinColumn = "parent_id"
                    }
                },
            )
        }
        expectFailure("missing mappedBy", "Relation 'children' in $sampleClass must declare mappedBy") {
            validator.validate(
                sampleClass,
                baseProperties,
                entity(sampleClass).apply {
                    relations["children"] = JpaRelationSpec().apply {
                        type = "oneToMany"
                        target = relationParent
                    }
                },
            )
        }
    }

    private fun entity(domainClass: String, id: String = "id"): JpaEntitySpec =
        JpaEntitySpec(domainClass).apply {
            table = "sample"
            this.id = id
        }

    private fun expectFailure(label: String, expectedMessage: String, action: () -> Unit) {
        try {
            action()
        } catch (ex: GradleException) {
            if (!ex.message.orEmpty().contains(expectedMessage)) {
                throw GradleException(
                    "Negative case '$label' failed with unexpected message: ${ex.message}; " +
                        "expected to contain: $expectedMessage",
                    ex,
                )
            }
            return
        }
        throw GradleException("Negative case '$label' did not fail")
    }
}
