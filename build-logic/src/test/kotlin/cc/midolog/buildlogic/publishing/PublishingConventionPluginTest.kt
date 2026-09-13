package cc.midolog.buildlogic.publishing

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class PublishingConventionPluginTest {

    @Test
    fun `fixture projects publish with custom artifactId and sources jar`() {
        val rootDir = Files.createTempDirectory("publishing-test")
        val repoDir = rootDir.resolve("custom-repo")

        rootDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "fixture-root"
            include("storage:jpa")
            include("support:web")
            include("core:domain")
            """.trimIndent()
        )

        rootDir.resolve("build.gradle.kts").writeText(
            """
            allprojects {
                group = "cc.midolog"
                version = "0.0.1-SNAPSHOT"
            }
            """.trimIndent()
        )

        val storageJpaDir = rootDir.resolve("storage/jpa").createDirectories()
        storageJpaDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("cc.midolog.publishing")
            }
            """.trimIndent()
        )
        val storageJpaSrc = storageJpaDir.resolve("src/main/java/sample").createDirectories()
        storageJpaSrc.resolve("JpaSample.java").writeText("package sample; public class JpaSample {}")

        val supportWebDir = rootDir.resolve("support/web").createDirectories()
        supportWebDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("cc.midolog.publishing")
            }
            """.trimIndent()
        )
        val supportWebSrc = supportWebDir.resolve("src/main/java/sample").createDirectories()
        supportWebSrc.resolve("WebSample.java").writeText("package sample; public class WebSample {}")

        val coreDomainDir = rootDir.resolve("core/domain").createDirectories()
        coreDomainDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                `java-test-fixtures`
                id("cc.midolog.publishing")
            }
            """.trimIndent()
        )
        val domainSrc = coreDomainDir.resolve("src/main/java/sample").createDirectories()
        domainSrc.resolve("DomainSample.java").writeText("package sample; public class DomainSample {}")
        val fixturesSrc = coreDomainDir.resolve("src/testFixtures/java/sample").createDirectories()
        fixturesSrc.resolve("FixtureSample.java").writeText("package sample; public class FixtureSample {}")

        val result = GradleRunner.create()
            .withProjectDir(rootDir.toFile())
            .withPluginClasspath()
            .withArguments("publishAllToLocalRepo", "-PbackendInitRepo=${repoDir.toAbsolutePath()}")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":publishAllToLocalRepo")?.outcome)

        // storage:jpa verification
        val jpaGroupDir = repoDir.resolve("cc/midolog/backend-init-storage-jpa/0.0.1-SNAPSHOT")
        assertTrue(jpaGroupDir.exists(), "storage-jpa repo directory must exist")
        val jpaPom = Files.list(jpaGroupDir).filter { it.fileName.toString().endsWith(".pom") }.findFirst().orElse(null)
        val jpaSourcesJar = Files.list(jpaGroupDir).filter { it.fileName.toString().endsWith("-sources.jar") }.findFirst().orElse(null)
        assertTrue(jpaPom != null && jpaPom.exists(), "storage-jpa pom must exist: $jpaPom")
        assertTrue(jpaSourcesJar != null && jpaSourcesJar.exists(), "storage-jpa sources jar must exist: $jpaSourcesJar")
        val jpaPomContent = jpaPom.readText()
        assertTrue(
            jpaPomContent.contains("<artifactId>backend-init-storage-jpa</artifactId>"),
            "POM artifactId must be backend-init-storage-jpa"
        )

        // support:web verification
        val webGroupDir = repoDir.resolve("cc/midolog/backend-init-support-web/0.0.1-SNAPSHOT")
        assertTrue(webGroupDir.exists(), "support-web repo directory must exist")
        val webPom = Files.list(webGroupDir).filter { it.fileName.toString().endsWith(".pom") }.findFirst().orElse(null)
        val webSourcesJar = Files.list(webGroupDir).filter { it.fileName.toString().endsWith("-sources.jar") }.findFirst().orElse(null)
        assertTrue(webPom != null && webPom.exists(), "support-web pom must exist: $webPom")
        assertTrue(webSourcesJar != null && webSourcesJar.exists(), "support-web sources jar must exist: $webSourcesJar")
        val webPomContent = webPom.readText()
        assertTrue(
            webPomContent.contains("<artifactId>backend-init-support-web</artifactId>"),
            "POM artifactId must be backend-init-support-web"
        )

        // core:domain verification (core prefix stripped, test-fixtures published)
        val domainGroupDir = repoDir.resolve("cc/midolog/backend-init-domain/0.0.1-SNAPSHOT")
        assertTrue(domainGroupDir.exists(), "core:domain repo directory must exist")
        val domainPom = Files.list(domainGroupDir).filter { it.fileName.toString().endsWith(".pom") }.findFirst().orElse(null)
        val domainSourcesJar = Files.list(domainGroupDir).filter { it.fileName.toString().endsWith("-sources.jar") }.findFirst().orElse(null)
        val domainTestFixturesJar = Files.list(domainGroupDir).filter { it.fileName.toString().endsWith("-test-fixtures.jar") }.findFirst().orElse(null)
        assertTrue(domainPom != null && domainPom.exists(), "domain pom must exist: $domainPom")
        assertTrue(domainSourcesJar != null && domainSourcesJar.exists(), "domain sources jar must exist: $domainSourcesJar")
        assertTrue(domainTestFixturesJar != null && domainTestFixturesJar.exists(), "domain test fixtures jar must exist: $domainTestFixturesJar")
        val domainPomContent = domainPom.readText()
        assertTrue(
            domainPomContent.contains("<artifactId>backend-init-domain</artifactId>"),
            "POM artifactId must be backend-init-domain"
        )
    }

    @Test
    fun `bom fixture publishes with backend-init-bom artifactId and contains target dependency constraints`() {
        val rootDir = Files.createTempDirectory("bom-publishing-test")
        val repoDir = rootDir.resolve("custom-repo")

        rootDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "fixture-root"
            include("platform:bom")
            include("storage:jpa")
            """.trimIndent()
        )

        rootDir.resolve("build.gradle.kts").writeText(
            """
            allprojects {
                group = "cc.midolog"
                version = "0.0.1-SNAPSHOT"
            }
            """.trimIndent()
        )

        val jpaDir = rootDir.resolve("storage/jpa").createDirectories()
        jpaDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("cc.midolog.publishing")
            }
            """.trimIndent()
        )

        val bomDir = rootDir.resolve("platform/bom").createDirectories()
        bomDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-platform`
                id("cc.midolog.publishing")
            }
            javaPlatform {
                allowDependencies()
            }
            dependencies {
                constraints {
                    api(project(":storage:jpa"))
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(rootDir.toFile())
            .withPluginClasspath()
            .withArguments("publishAllToLocalRepo", "-PbackendInitRepo=${repoDir.toAbsolutePath()}")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":publishAllToLocalRepo")?.outcome)

        val bomGroupDir = repoDir.resolve("cc/midolog/backend-init-bom/0.0.1-SNAPSHOT")
        assertTrue(bomGroupDir.exists(), "BOM repo directory must exist")
        val bomPom = Files.list(bomGroupDir).filter { it.fileName.toString().endsWith(".pom") }.findFirst().orElse(null)
        assertTrue(bomPom != null && bomPom.exists(), "BOM pom must exist: $bomPom")

        val pomContent = bomPom.readText()
        assertTrue(
            pomContent.contains("<artifactId>backend-init-bom</artifactId>"),
            "BOM POM artifactId must be backend-init-bom"
        )
        assertTrue(
            pomContent.contains("<artifactId>backend-init-storage-jpa</artifactId>"),
            "BOM POM must contain backend-init-storage-jpa constraint"
        )
    }
}
