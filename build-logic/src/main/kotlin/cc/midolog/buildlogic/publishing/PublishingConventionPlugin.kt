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
 * 아티팩트 ID 명명 규칙, 소스 Jar 포함, 엄격한 URL 보안 검증, 사전 존재 여부 preflight 검사 및 자격 증명 검증을 제공한다.
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

        // 2. 원격 GitHub Packages 레지스트리 구성 (엄격한 URL 보안 검증 적용)
        val rawGithubRepoUrl = project.findProperty("backendInitGithubRepoUrl")?.toString()
            ?: System.getenv("BACKEND_INIT_GITHUB_REPO_URL")
            ?: GithubPackagesUrlValidator.DEFAULT_REPO_URL

        val allowInsecureTestUrl = project.findProperty("cc.midolog.allowInsecureTestUrl")?.toString()?.toBoolean() ?: false

        // URL 유효성 검증: HTTPS, maven.pkg.github.com, /dev-inho/backend-init 및 파라미터/외부호스트 차단
        val validatedUri = GithubPackagesUrlValidator.validate(rawGithubRepoUrl, allowInsecureTestUrl)

        publishing.repositories.maven { repo ->
            repo.name = "github"
            repo.setUrl(validatedUri)
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

        // 4. 루트 사전 검사 태스크: 원격 아티팩트 사전 존재 여부 preflight 검사
        val rootPreflightTask = if (project.rootProject.tasks.findByName("preflightCheckRemoteArtifacts") != null) {
            project.rootProject.tasks.getByName("preflightCheckRemoteArtifacts")
        } else {
            project.rootProject.tasks.create("preflightCheckRemoteArtifacts") { task ->
                task.group = "publishing"
                task.description = "Preflight check ensuring target release artifacts do not already exist on remote GitHub Packages before publishing"
                task.doFirst {
                    val password = resolveGithubPassword(project)
                    if (password.isBlank()) {
                        throw org.gradle.api.GradleException(
                            "GitHub Packages credentials missing: GITHUB_TOKEN (or GH_TOKEN / gpr.key) must be provided to publish to GitHub Packages repository."
                        )
                    }

                    val allowOverwrite = project.findProperty("allowReleaseOverwrite")?.toString()?.toBoolean() ?: false
                    if (allowOverwrite) {
                        project.logger.warn("Release overwrite guard is bypassed via allowReleaseOverwrite=true")
                        return@doFirst
                    }

                    // cc.midolog.publishing 플러그인이 적용된 모든 하위 모듈 수집
                    val publishingProjects = project.rootProject.subprojects.filter { sub ->
                        sub.plugins.hasPlugin("cc.midolog.publishing")
                    }
                    val targetArtifacts = publishingProjects.map { sub ->
                        val artId = when {
                            sub.path == ":platform:bom" -> "backend-init-bom"
                            sub.path.startsWith(":core:") -> "backend-init-" + sub.path.removePrefix(":core:").replace(':', '-')
                            sub.path.startsWith(":") -> "backend-init-" + sub.path.removePrefix(":").replace(':', '-')
                            else -> "backend-init-" + sub.name
                        }
                        RemoteArtifactPreflightChecker.TargetArtifact(
                            groupId = sub.group.toString().ifBlank { "cc.midolog" },
                            artifactId = artId,
                            version = sub.version.toString(),
                        )
                    }

                    val targetUrl = project.findProperty("backendInitGithubRepoUrl")?.toString()
                        ?: System.getenv("BACKEND_INIT_GITHUB_REPO_URL")
                        ?: GithubPackagesUrlValidator.DEFAULT_REPO_URL
                    val allowTest = project.findProperty("cc.midolog.allowInsecureTestUrl")?.toString()?.toBoolean() ?: false

                    val checker = RemoteArtifactPreflightChecker(allowInsecureTestUrl = allowTest)
                    checker.checkAll(
                        baseUrl = targetUrl,
                        username = resolveGithubUsername(project),
                        token = password,
                        artifacts = targetArtifacts,
                        logger = { msg -> project.logger.lifecycle(msg) },
                    )
                }
            }
        }

        // 5. 루트 집계 태스크: GitHub Packages 원격 배포
        val rootGithubPublishTask = if (project.rootProject.tasks.findByName("publishAllToGithubPackages") != null) {
            project.rootProject.tasks.getByName("publishAllToGithubPackages")
        } else {
            project.rootProject.tasks.create("publishAllToGithubPackages") { task ->
                task.group = "publishing"
                task.description = "Publishes all eligible library modules and BOM to GitHub Packages Maven repository"
                task.dependsOn(rootPreflightTask)
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
                githubPublishTask.dependsOn(rootPreflightTask)
                githubPublishTask.mustRunAfter(rootPreflightTask)
                rootGithubPublishTask.dependsOn(githubPublishTask)
                githubPublishTask.doFirst {
                    val password = resolveGithubPassword(project)
                    if (password.isBlank()) {
                        throw org.gradle.api.GradleException(
                            "GitHub Packages credentials missing: GITHUB_TOKEN (or GH_TOKEN / gpr.key) must be provided to publish to GitHub Packages repository."
                        )
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
