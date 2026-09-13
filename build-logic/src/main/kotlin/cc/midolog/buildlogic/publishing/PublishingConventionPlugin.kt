package cc.midolog.buildlogic.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

class PublishingConventionPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.apply("maven-publish")

        val customArtifactId = when {
            project.path == ":platform:bom" -> "backend-init-bom"
            project.path.startsWith(":core:") -> "backend-init-" + project.path.removePrefix(":core:").replace(':', '-')
            project.path.startsWith(":") -> "backend-init-" + project.path.removePrefix(":").replace(':', '-')
            else -> "backend-init-" + project.name
        }

        val publishing = project.extensions.getByType(PublishingExtension::class.java)

        val repoDir = if (project.hasProperty("backendInitRepo")) {
            project.file(project.property("backendInitRepo")!!)
        } else {
            project.rootProject.file("build/repo")
        }

        publishing.repositories.maven { repo ->
            repo.name = "localRepo"
            repo.setUrl(repoDir.toURI())
        }

        val rootPublishTask = if (project.rootProject.tasks.findByName("publishAllToLocalRepo") != null) {
            project.rootProject.tasks.getByName("publishAllToLocalRepo")
        } else {
            project.rootProject.tasks.create("publishAllToLocalRepo") { task ->
                task.group = "publishing"
                task.description = "Publishes all eligible library modules and BOM to the local repository"
                task.doLast {
                    project.logger.lifecycle("Published all eligible modules to local repo")
                }
            }
        }

        project.plugins.withId("java") {
            project.extensions.findByType(JavaPluginExtension::class.java)?.withSourcesJar()
            if (publishing.publications.findByName("mavenJava") == null) {
                publishing.publications.create("mavenJava", MavenPublication::class.java) { pub ->
                    pub.from(project.components.getByName("java"))
                    pub.artifactId = customArtifactId
                    pub.groupId = project.group.toString()
                    pub.version = project.version.toString()
                }
            }
        }

        project.plugins.withId("java-platform") {
            if (publishing.publications.findByName("mavenJava") == null) {
                publishing.publications.create("mavenJava", MavenPublication::class.java) { pub ->
                    pub.from(project.components.getByName("javaPlatform"))
                    pub.artifactId = customArtifactId
                    pub.groupId = project.group.toString()
                    pub.version = project.version.toString()
                }
            }
        }

        project.afterEvaluate {
            val publishTask = project.tasks.findByName("publishMavenJavaPublicationToLocalRepoRepository")
            if (publishTask != null) {
                rootPublishTask.dependsOn(publishTask)
            }
        }
    }
}
