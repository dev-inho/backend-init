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
            include("custom:jpa")
            include("core:jpa")
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

        val customJpaDir = rootDir.resolve("custom/jpa").createDirectories()
        customJpaDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("cc.midolog.publishing")
            }
            """.trimIndent()
        )
        val customJpaSrc = customJpaDir.resolve("src/main/java/sample").createDirectories()
        customJpaSrc.resolve("CustomJpaSample.java").writeText("package sample; public class CustomJpaSample {}")

        val coreJpaDir = rootDir.resolve("core/jpa").createDirectories()
        coreJpaDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("cc.midolog.publishing")
            }
            """.trimIndent()
        )
        val coreJpaSrc = coreJpaDir.resolve("src/main/java/sample").createDirectories()
        coreJpaSrc.resolve("CoreJpaSample.java").writeText("package sample; public class CoreJpaSample {}")

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

        // custom:jpa verification (same terminal name 'jpa', distinct path prefix 'custom')
        val customJpaGroupDir = repoDir.resolve("cc/midolog/backend-init-custom-jpa/0.0.1-SNAPSHOT")
        assertTrue(customJpaGroupDir.exists(), "custom-jpa repo directory must exist to prevent collision")
        val customJpaPom = Files.list(customJpaGroupDir).filter { it.fileName.toString().endsWith(".pom") }.findFirst().orElse(null)
        val customJpaSourcesJar = Files.list(customJpaGroupDir).filter { it.fileName.toString().endsWith("-sources.jar") }.findFirst().orElse(null)
        assertTrue(customJpaPom != null && customJpaPom.exists(), "custom-jpa pom must exist: $customJpaPom")
        assertTrue(customJpaSourcesJar != null && customJpaSourcesJar.exists(), "custom-jpa sources jar must exist: $customJpaSourcesJar")
        val customJpaPomContent = customJpaPom.readText()
        assertTrue(
            customJpaPomContent.contains("<artifactId>backend-init-custom-jpa</artifactId>"),
            "POM artifactId must be backend-init-custom-jpa"
        )

        // core:jpa verification (same terminal name 'jpa', core prefix stripped)
        val coreJpaGroupDir = repoDir.resolve("cc/midolog/backend-init-jpa/0.0.1-SNAPSHOT")
        assertTrue(coreJpaGroupDir.exists(), "core-jpa repo directory must exist to prevent collision")
        val coreJpaPom = Files.list(coreJpaGroupDir).filter { it.fileName.toString().endsWith(".pom") }.findFirst().orElse(null)
        val coreJpaSourcesJar = Files.list(coreJpaGroupDir).filter { it.fileName.toString().endsWith("-sources.jar") }.findFirst().orElse(null)
        assertTrue(coreJpaPom != null && coreJpaPom.exists(), "core-jpa pom must exist: $coreJpaPom")
        assertTrue(coreJpaSourcesJar != null && coreJpaSourcesJar.exists(), "core-jpa sources jar must exist: $coreJpaSourcesJar")
        val coreJpaPomContent = coreJpaPom.readText()
        assertTrue(
            coreJpaPomContent.contains("<artifactId>backend-init-jpa</artifactId>"),
            "POM artifactId must be backend-init-jpa"
        )

        // Verify that all modules sharing terminal name 'jpa' have distinct artifactIds
        val regex = Regex("<artifactId>(.*?)</artifactId>")
        val storageArtifactId = regex.find(jpaPomContent)?.groupValues?.get(1)
        val customArtifactId = regex.find(customJpaPomContent)?.groupValues?.get(1)
        val coreArtifactId = regex.find(coreJpaPomContent)?.groupValues?.get(1)

        val jpaArtifactIds = setOf(storageArtifactId, customArtifactId, coreArtifactId)
        assertEquals(3, jpaArtifactIds.size, "All 3 modules sharing terminal name 'jpa' must have mutually distinct artifactIds: $jpaArtifactIds")

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

    @Test
    fun `remote publishing fails fast when credentials are missing`() {
        val rootDir = Files.createTempDirectory("remote-fail-test")
        rootDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "fixture-root"
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

        val result = GradleRunner.create()
            .withProjectDir(rootDir.toFile())
            .withPluginClasspath()
            .withArguments("publishAllToGithubPackages")
            .buildAndFail()

        assertTrue(
            result.output.contains("GitHub Packages credentials missing"),
            "Build failure output must clearly state missing credentials. Actual output:\n${result.output}"
        )
    }

    @Test
    fun `arbitrary insecure url override fails fast with security exception preventing credential leak`() {
        val rootDir = Files.createTempDirectory("insecure-url-test")

        rootDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "fixture-root"
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

        val result = GradleRunner.create()
            .withProjectDir(rootDir.toFile())
            .withPluginClasspath()
            .withArguments(
                "tasks",
                "-PbackendInitGithubRepoUrl=http://attacker.com/dev-inho/backend-init",
                "-Pgpr.key=secret-token"
            )
            .buildAndFail()

        assertTrue(
            result.output.contains("SecurityException") || result.output.contains("HTTPS scheme"),
            "Build must fail immediately on insecure HTTP scheme. Actual output:\n${result.output}"
        )
    }

    @Test
    fun `arbitrary external host override fails fast with security exception`() {
        val rootDir = Files.createTempDirectory("external-host-test")

        rootDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "fixture-root"
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

        val result = GradleRunner.create()
            .withProjectDir(rootDir.toFile())
            .withPluginClasspath()
            .withArguments(
                "tasks",
                "-PbackendInitGithubRepoUrl=https://evil.com/dev-inho/backend-init",
                "-Pgpr.key=secret-token"
            )
            .buildAndFail()

        assertTrue(
            result.output.contains("SecurityException") || result.output.contains("maven.pkg.github.com"),
            "Build must fail immediately on non-github host. Actual output:\n${result.output}"
        )
    }

    @Test
    fun `preflight existence check blocks publishing when remote artifact already exists`() {
        val rootDir = Files.createTempDirectory("preflight-conflict-test")

        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()

        try {
            rootDir.resolve("settings.gradle.kts").writeText(
                """
                rootProject.name = "fixture-root"
                include("storage:jpa")
                """.trimIndent()
            )
            rootDir.resolve("build.gradle.kts").writeText(
                """
                allprojects {
                    group = "cc.midolog"
                    version = "1.0.0"
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

            val result = GradleRunner.create()
                .withProjectDir(rootDir.toFile())
                .withPluginClasspath()
                .withArguments(
                    "publishAllToGithubPackages",
                    "-PbackendInitGithubRepoUrl=http://127.0.0.1:$port/dev-inho/backend-init",
                    "-Pcc.midolog.allowInsecureTestUrl=true",
                    "-Pgpr.user=test-user",
                    "-Pgpr.key=dummy-token"
                )
                .buildAndFail()

            assertTrue(
                result.output.contains("Remote release artifact already exists"),
                "Preflight check must fail when artifact already exists. Actual output:\n${result.output}"
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `preflight check passes and proceeds when remote artifact does not exist (404)`() {
        val rootDir = Files.createTempDirectory("preflight-pass-test")

        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/") { exchange ->
            if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                // PUT or other upload methods: consume body completely
                exchange.requestBody.readAllBytes()
                exchange.sendResponseHeaders(200, -1)
            }
            exchange.close()
        }
        server.start()

        try {
            rootDir.resolve("settings.gradle.kts").writeText(
                """
                rootProject.name = "fixture-root"
                include("storage:jpa")
                """.trimIndent()
            )
            rootDir.resolve("build.gradle.kts").writeText(
                """
                allprojects {
                    group = "cc.midolog"
                    version = "1.0.0"
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
            val jpaSrc = jpaDir.resolve("src/main/java/sample").createDirectories()
            jpaSrc.resolve("JpaSample.java").writeText("package sample; public class JpaSample {}")

            val result = GradleRunner.create()
                .withProjectDir(rootDir.toFile())
                .withPluginClasspath()
                .withArguments(
                    "preflightCheckRemoteArtifacts",
                    "-PbackendInitGithubRepoUrl=http://127.0.0.1:$port/dev-inho/backend-init",
                    "-Pcc.midolog.allowInsecureTestUrl=true",
                    "-Pgpr.user=test-user",
                    "-Pgpr.key=dummy-token"
                )
                .build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":preflightCheckRemoteArtifacts")?.outcome)
            assertTrue(
                result.output.contains("Preflight check passed"),
                "Preflight check should log success message. Actual output:\n${result.output}"
            )
        } finally {
            server.stop(0)
        }
    }
}
