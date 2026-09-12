package cc.midolog.buildlogic.jpadsl

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateJpaDslSourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val domainSourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val specsFingerprint: Property<String>

    @get:Internal
    var specsProvider: (() -> List<JpaEntitySpec>)? = null

    init {
        group = "jpa dsl"
        description = "Generates JPA entities, repositories, and mappers from the typed JPA DSL."
    }

    @TaskAction
    fun generate() {
        val specs = specsProvider?.invoke().orEmpty()
        if (specs.isEmpty()) {
            throw GradleException("jpaDsl must declare at least one entity")
        }

        val outputRoot = outputDir.get().asFile
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()

        val parser = DomainSourceParser()
        val validator = JpaDslValidator()
        val renderer = JpaDslRenderer()
        val domainRoot = domainSourceDir.get().asFile

        specs.forEach { spec ->
            val domainFile = File(domainRoot, spec.domainClass.replace('.', '/') + ".kt")
            val properties = parser.parse(spec.domainClass, domainFile)
            val idProperty = validator.validate(spec.domainClass, properties, spec)
            renderer.render(spec, properties, idProperty, outputRoot)
        }
    }
}

internal fun String.simpleName(): String = substringAfterLast('.')

internal fun String.isNullableKotlinType(): Boolean = endsWith("?")

internal fun defaultValueForKotlinType(type: String): String {
    if (type.endsWith("?")) {
        return "null"
    }
    return when (type) {
        "String" -> "\"\""
        "Int" -> "0"
        "Long" -> "0L"
        "Double" -> "0.0"
        "Float" -> "0.0f"
        "Boolean" -> "false"
        "java.time.Instant" -> "java.time.Instant.EPOCH"
        else -> "throw IllegalStateException(\"$type must be set before persistence\")"
    }
}

internal fun defaultValueForProperty(type: String, fieldSpec: JpaFieldSpec): String =
    if (fieldSpec.enumStrategy != null && !type.endsWith("?")) {
        "${type}.entries.first()"
    } else {
        defaultValueForKotlinType(type)
    }

internal fun entityTypeFor(property: DomainProperty, fieldSpec: JpaFieldSpec): String =
    fieldSpec.storageType ?: property.type

internal fun importableKotlinType(domainPackage: String, type: String): String? {
    val normalized = type.removeSuffix("?")
    if (normalized.contains("<") || normalized.contains(">")) {
        return null
    }
    if (normalized.contains(".")) {
        return normalized
    }
    if (normalized in setOf("String", "Int", "Long", "Double", "Float", "Boolean")) {
        return null
    }
    return "$domainPackage.$normalized"
}

internal fun List<JpaEntitySpec>.fingerprint(): String =
    joinToString("|") { spec ->
        buildString {
            append(spec.domainClass).append(':').append(spec.table).append(':').append(spec.id)
            spec.fields.forEach { (name, field) ->
                append(":field=").append(name)
                    .append(",column=").append(field.column)
                    .append(",nullable=").append(field.nullable)
                    .append(",enum=").append(field.enumStrategy)
                    .append(",storageType=").append(field.storageType)
                    .append(",converter=").append(field.converter)
                    .append(",relation=").append(field.relation)
            }
            spec.relations.forEach { (name, relation) ->
                append(":relation=").append(name)
                    .append(",type=").append(relation.type)
                    .append(",target=").append(relation.target)
                    .append(",sourceField=").append(relation.sourceField)
                    .append(",joinColumn=").append(relation.joinColumn)
                    .append(",referencedColumn=").append(relation.referencedColumn)
                    .append(",mappedBy=").append(relation.mappedBy)
                    .append(",toDomain=").append(relation.toDomain)
            }
        }
    }
