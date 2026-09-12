package cc.midolog.sample

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class DomainPurityTest {

    @Test
    fun `domain source stays free of Spring JPA and MyBatis dependencies`() {
        val sourceRoot = Path.of("src/main/kotlin")
        val userSourceRoot = sourceRoot.resolve("cc/midolog/user")
        val fileSourceRoot = sourceRoot.resolve("cc/midolog/file")
        val forbiddenTokens = listOf(
            "org.springframework",
            "jakarta.persistence",
            "javax.persistence",
            "org.jetbrains.exposed",
            "org.mybatis",
            "com.google.devtools.ksp",
            "@Entity",
            "@Table",
            "@Id",
            "@Column",
            "@MappedSuperclass",
            "@Embeddable",
            "@Repository",
            "@Component",
            "@Service",
            "@DomainEntity",
            "@GenerateJpa",
        )

        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { path ->
                    val text = path.readText()
                    forbiddenTokens
                        .filter { token -> text.contains(token) }
                        .map { token -> "$path contains $token" }
                        .stream()
                }
                .toList()
        }

        assertTrue(Files.isDirectory(userSourceRoot), "user domain package should exist")
        assertTrue(Files.isDirectory(fileSourceRoot), "file domain package should exist")
        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }
}
