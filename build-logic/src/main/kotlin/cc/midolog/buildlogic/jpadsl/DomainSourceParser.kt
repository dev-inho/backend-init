package cc.midolog.buildlogic.jpadsl

import java.io.File
import org.gradle.api.GradleException

class DomainSourceParser {
    fun parse(domainClass: String, domainFile: File): List<DomainProperty> {
        val domainSimpleName = domainClass.simpleName()
        if (!domainFile.isFile) {
            throw GradleException("Domain source not found for $domainClass: $domainFile")
        }

        val source = domainFile.readText()
        val classRegex = Regex(
            "data\\s+class\\s+${Regex.escape(domainSimpleName)}\\s*\\((.*?)\\n\\)",
            RegexOption.DOT_MATCHES_ALL,
        )
        val constructorBody = classRegex.find(source)?.groupValues?.get(1)
            ?: throw GradleException("Only primary-constructor data classes are supported: $domainClass")

        return constructorBody
            .split(Regex(",\\s*\\n|,\\s*(?=(?:val|var)\\s)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { declaration ->
                val property = Regex("(?:val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([^=,\\n)]+)")
                    .find(declaration)
                    ?: throw GradleException("Unsupported constructor property in $domainClass: $declaration")

                DomainProperty(
                    name = property.groupValues[1],
                    type = property.groupValues[2].trim(),
                )
            }
    }
}
