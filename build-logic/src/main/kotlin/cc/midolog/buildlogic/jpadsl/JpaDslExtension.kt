package cc.midolog.buildlogic.jpadsl

import groovy.lang.Closure
import org.gradle.api.Action

open class JpaDslExtension {
    var generatedSourceDir: String = "generated/sources/jpaDsl/main/kotlin"
    var domainProjectPath: String = ":core:domain"

    internal val entities: MutableList<JpaEntitySpec> = mutableListOf()

    fun entity(domainClass: String, configure: Action<JpaEntitySpec>) {
        val spec = JpaEntitySpec(domainClass)
        configure.execute(spec)
        entities += spec
    }

    fun entity(domainClass: String, configure: Closure<*>) {
        val spec = JpaEntitySpec(domainClass)
        configure.delegate = spec
        configure.resolveStrategy = Closure.DELEGATE_FIRST
        configure.call()
        entities += spec
    }

    internal fun specs(): List<JpaEntitySpec> = entities.toList()
}

open class JpaEntitySpec(
    var domainClass: String,
) {
    var table: String = ""
    var id: String = "id"

    internal val fields: MutableMap<String, JpaFieldSpec> = linkedMapOf()
    internal val relations: MutableMap<String, JpaRelationSpec> = linkedMapOf()

    fun field(name: String, configure: Action<JpaFieldSpec>) {
        val spec = JpaFieldSpec()
        configure.execute(spec)
        fields[name] = spec
    }

    fun field(name: String, configure: Closure<*>) {
        val spec = JpaFieldSpec()
        configure.delegate = spec
        configure.resolveStrategy = Closure.DELEGATE_FIRST
        configure.call()
        fields[name] = spec
    }

    fun relation(name: String, configure: Action<JpaRelationSpec>) {
        val spec = JpaRelationSpec()
        configure.execute(spec)
        relations[name] = spec
    }

    fun relation(name: String, configure: Closure<*>) {
        val spec = JpaRelationSpec()
        configure.delegate = spec
        configure.resolveStrategy = Closure.DELEGATE_FIRST
        configure.call()
        relations[name] = spec
    }
}

open class JpaFieldSpec {
    var column: String? = null
    var nullable: Boolean? = null
    var enumStrategy: String? = null
    var storageType: String? = null
    var converter: String? = null
    var relation: String? = null
}

open class JpaRelationSpec {
    var type: String = ""
    var target: String = ""
    var sourceField: String? = null
    var joinColumn: String? = null
    var referencedColumn: String? = null
    var mappedBy: String? = null
    var toDomain: String? = null
}

data class DomainProperty(
    val name: String,
    val type: String,
)
