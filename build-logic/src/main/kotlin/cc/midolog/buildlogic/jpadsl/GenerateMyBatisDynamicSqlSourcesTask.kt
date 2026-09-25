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

abstract class GenerateMyBatisDynamicSqlSourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val domainSourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val resourcesOutputDir: DirectoryProperty

    @get:Input
    abstract val specsFingerprint: Property<String>

    @get:Internal
    var specsProvider: (() -> List<JpaEntitySpec>)? = null

    init {
        group = "mybatis dsl"
        description = "Generates MyBatis Dynamic SQL support classes from the typed DSL."
    }

    @TaskAction
    fun generate() {
        val specs = specsProvider?.invoke().orEmpty()
        if (specs.isEmpty()) {
            throw GradleException("mybatisDynamicSql must declare at least one entity")
        }

        val outputRoot = outputDir.get().asFile
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()

        val resourcesRoot = resourcesOutputDir.get().asFile
        resourcesRoot.deleteRecursively()
        resourcesRoot.mkdirs()

        val parser = DomainSourceParser()
        val renderer = MyBatisDynamicSqlRenderer()
        val domainRoot = domainSourceDir.get().asFile

        val customerAutoConfigs = mutableListOf<String>()

        specs.forEach { spec ->
            validateMyBatisEntitySpec(spec)
            val domainFile = File(domainRoot, spec.domainClass.replace('.', '/') + ".kt")
            val resolvedFile = try {
                parser.resolveDomainFile(spec.domainClass, domainFile)
            } catch (_: Exception) {
                null
            }
            if (resolvedFile != null && resolvedFile.isFile) {
                val properties = parser.parse(spec.domainClass, resolvedFile)
                renderer.render(spec, properties, outputRoot)

                if (spec.domainClass.contains(".customers.")) {
                    val domainPackage = spec.domainClass.substringBeforeLast('.')
                    val domainSimpleName = spec.domainClass.simpleName()
                    val contextPackage = domainPackage
                        .removePrefix("cc.midolog.")
                        .removeSuffix(".model")
                    customerAutoConfigs.add("cc.midolog.storage.mybatis.$contextPackage.${domainSimpleName}MyBatisAutoConfiguration")
                }
            }
        }

        if (customerAutoConfigs.isNotEmpty()) {
            val importsFile = File(resourcesRoot, "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            importsFile.parentFile.mkdirs()
            importsFile.writeText(customerAutoConfigs.joinToString("\n") + "\n")
        }
    }
}
