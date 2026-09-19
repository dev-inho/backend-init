package cc.midolog.buildlogic.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

/**
 * 라이브러리 모듈 및 BOM의 표준 메이븐 퍼블리싱 규약을 정의하는 플러그인.
 *
 * 로컬 저장소(build/repo) 및 원격 GitHub Packages Maven 레지스트리 배포를 구성하며,
 * 아티팩트 ID 명명 규칙, 소스 Jar 포함, 자격 증명 주입 및 원격 게시 시 자격 증명 검증을 제공한다.
 */
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

        // 1. 로컬 저장소 구성
        val repoDir = if (project.hasProperty("backendInitRepo")) {
            project.file(project.property("backendInitRepo")!!)
        } else {
            project.rootProject.file("build/repo")
        }

        publishing.repositories.maven { repo ->
            repo.name = "localRepo"
            repo.setUrl(repoDir.toURI())
        }

        // 2. 원격 GitHub Packages 레지스트리 구성
        val githubRepoUrl = project.findProperty("backendInitGithubRepoUrl")?.toString()
            ?: System.getenv("BACKEND_INIT_GITHUB_REPO_URL")
            ?: "https://maven.pkg.github.com/dev-inho/backend-init"

        publishing.repositories.maven { repo ->
            repo.name = "github"
            repo.setUrl(project.uri(githubRepoUrl))
            val scheme = repo.url.scheme
            if (scheme != null && scheme.startsWith("http", ignoreCase = true)) {
                repo.credentials { credentials ->
                    credentials.username = resolveGithubUsername(project)
                    credentials.password = resolveGithubPassword(project)
                }
            }
        }

        // 3. 루트 집계 태스크: 로컬 배포
        val rootLocalPublishTask = if (project.rootProject.tasks.findByName("publishAllToLocalRepo") != null) {
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

        // 4. 루트 집계 태스크: GitHub Packages 원격 배포
        val rootGithubPublishTask = if (project.rootProject.tasks.findByName("publishAllToGithubPackages") != null) {
            project.rootProject.tasks.getByName("publishAllToGithubPackages")
        } else {
            project.rootProject.tasks.create("publishAllToGithubPackages") { task ->
                task.group = "publishing"
                task.description = "Publishes all eligible library modules and BOM to GitHub Packages Maven repository"
                task.doFirst {
                    val password = resolveGithubPassword(project)
                    if (password.isBlank()) {
                        throw org.gradle.api.GradleException(
                            "GitHub Packages credentials missing: GITHUB_TOKEN (or GH_TOKEN / gpr.key) must be provided to publish to GitHub Packages repository."
                        )
                    }
                }
                task.doLast {
                    project.logger.lifecycle("Published all eligible modules to GitHub Packages repository")
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
            val localPublishTask = project.tasks.findByName("publishMavenJavaPublicationToLocalRepoRepository")
            if (localPublishTask != null) {
                rootLocalPublishTask.dependsOn(localPublishTask)
            }

            val githubPublishTask = project.tasks.findByName("publishMavenJavaPublicationToGithubRepository")
            if (githubPublishTask != null) {
                rootGithubPublishTask.dependsOn(githubPublishTask)
                githubPublishTask.doFirst {
                    val password = resolveGithubPassword(project)
                    if (password.isBlank()) {
                        throw org.gradle.api.GradleException(
                            "GitHub Packages credentials missing: GITHUB_TOKEN (or GH_TOKEN / gpr.key) must be provided to publish to GitHub Packages repository."
                        )
                    }
                    val versionStr = project.version.toString()
                    val isRelease = !versionStr.endsWith("-SNAPSHOT")
                    val allowOverwrite = project.findProperty("allowReleaseOverwrite")?.toString()?.toBoolean() ?: false
                    if (isRelease && !allowOverwrite) {
                        project.logger.info("Publishing release version $versionStr with non-overwriting release guard active")
                    }
                }
            }
        }
    }

    companion object {
        /**
         * GitHub Packages 인증용 사용자명을 환경 변수 또는 Gradle 프로퍼티로부터 안전하게 도출한다.
         */
        fun resolveGithubUsername(project: Project): String {
            return project.findProperty("gpr.user")?.toString()
                ?: System.getenv("GITHUB_ACTOR")
                ?: System.getenv("GITHUB_USERNAME")
                ?: project.findProperty("githubUsername")?.toString()
                ?: project.findProperty("backendInitGithubUser")?.toString()
                ?: "dev-inho"
        }

        /**
         * GitHub Packages 인증용 비밀번호(토큰)를 환경 변수 또는 Gradle 프로퍼티로부터 안전하게 도출한다.
         */
        fun resolveGithubPassword(project: Project): String {
            return project.findProperty("gpr.key")?.toString()
                ?: System.getenv("GITHUB_TOKEN")
                ?: System.getenv("GH_TOKEN")
                ?: project.findProperty("githubToken")?.toString()
                ?: project.findProperty("backendInitGithubToken")?.toString()
                ?: ""
        }
    }
}
