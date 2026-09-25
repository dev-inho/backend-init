package cc.midolog.examples.consumer

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ArtifactConsumerUrlSecurityTest {

    private fun runConsumerGradle(vararg args: String): Pair<Int, String> {
        val projectDir = File(System.getProperty("user.dir"))
        val gradlew = File(projectDir, "../../gradlew").canonicalFile
        val command = mutableListOf(gradlew.absolutePath, "-p", projectDir.absolutePath)
        command.addAll(args)

        val process = ProcessBuilder(command)
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return exitCode to output
    }

    @Test
    fun `insecure http url is rejected with security violation in consumer build`() {
        val (exitCode, output) = runConsumerGradle(
            "help",
            "-PbackendInitRepoUrl=http://attacker.com/dev-inho/backend-init",
            "-Pgpr.key=dummy-token"
        )

        assertTrue(exitCode != 0, "Build must fail")
        assertTrue(
            output.contains("Security violation: backendInitRepoUrl must use HTTPS scheme"),
            "Consumer build must fail when insecure HTTP URL is provided. Actual output:\n$output"
        )
    }

    @Test
    fun `arbitrary external host is rejected with security violation in consumer build`() {
        val (exitCode, output) = runConsumerGradle(
            "help",
            "-PbackendInitRepoUrl=https://evil.com/dev-inho/backend-init",
            "-Pgpr.key=dummy-token"
        )

        assertTrue(exitCode != 0, "Build must fail")
        assertTrue(
            output.contains("Security violation: backendInitRepoUrl host must be maven.pkg.github.com"),
            "Consumer build must fail when non-github host is provided. Actual output:\n$output"
        )
    }

    @Test
    fun `url with userInfo is rejected with security violation in consumer build`() {
        val (exitCode, output) = runConsumerGradle(
            "help",
            "-PbackendInitRepoUrl=https://user:pass@maven.pkg.github.com/dev-inho/backend-init",
            "-Pgpr.key=dummy-token"
        )

        assertTrue(exitCode != 0, "Build must fail")
        assertTrue(
            output.contains("must not contain user credentials"),
            "Consumer build must fail when userInfo is embedded in URL. Actual output:\n$output"
        )
    }

    @Test
    fun `url with different repository path is rejected in consumer build`() {
        val (exitCode, output) = runConsumerGradle(
            "help",
            "-PbackendInitRepoUrl=https://maven.pkg.github.com/other-org/other-repo",
            "-Pgpr.key=dummy-token"
        )

        assertTrue(exitCode != 0, "Build must fail")
        assertTrue(
            output.contains("repository path must be /dev-inho/backend-init"),
            "Consumer build must fail when different repository path is provided. Actual output:\n$output"
        )
    }

    @Test
    fun `random secret in userInfo is never leaked in consumer gradle output`() {
        val randomSecret = "CONSUMER_SEC_" + java.util.UUID.randomUUID().toString()
        val (exitCode, output) = runConsumerGradle(
            "help",
            "--stacktrace",
            "-PbackendInitRepoUrl=https://dummyUser:$randomSecret@maven.pkg.github.com/dev-inho/backend-init",
            "-Pgpr.key=dummy-token"
        )

        assertTrue(exitCode != 0, "Build must fail on userInfo")
        org.junit.jupiter.api.Assertions.assertFalse(
            output.contains(randomSecret),
            "Random secret must not appear anywhere in consumer gradle output or stacktrace"
        )
        assertTrue(
            output.contains("Security violation: backendInitRepoUrl must not contain user credentials (userInfo)"),
            "Output must state the violation and configuration key without leaking URL"
        )
    }

    @Test
    fun `random secret in query parameter is never leaked in consumer gradle output`() {
        val randomSecret = "CONSUMER_QUERY_SEC_" + java.util.UUID.randomUUID().toString()
        val (exitCode, output) = runConsumerGradle(
            "help",
            "--stacktrace",
            "-PbackendInitRepoUrl=https://maven.pkg.github.com/dev-inho/backend-init?token=$randomSecret",
            "-Pgpr.key=dummy-token"
        )

        assertTrue(exitCode != 0, "Build must fail on query parameters")
        org.junit.jupiter.api.Assertions.assertFalse(
            output.contains(randomSecret),
            "Random secret must not appear anywhere in consumer gradle output or stacktrace"
        )
        assertTrue(
            output.contains("Security violation: backendInitRepoUrl must not contain query parameters"),
            "Output must state the violation and configuration key without leaking URL"
        )
    }

    @Test
    fun `random secret in malformed url is never leaked in consumer gradle output`() {
        val randomSecret = "CONSUMER_MALFORMED_SEC_" + java.util.UUID.randomUUID().toString()
        val (exitCode, output) = runConsumerGradle(
            "help",
            "--stacktrace",
            "-PbackendInitRepoUrl=https://dummyUser:$randomSecret@[malformed-bracket/dev-inho/backend-init",
            "-Pgpr.key=dummy-token"
        )

        assertTrue(exitCode != 0, "Build must fail on malformed URI")
        org.junit.jupiter.api.Assertions.assertFalse(
            output.contains(randomSecret),
            "Random secret must not appear anywhere in consumer gradle output or stacktrace for malformed URI"
        )
        assertTrue(
            output.contains("Invalid repository URL format for backendInitRepoUrl: malformed URI"),
            "Output must state the malformed format and configuration key without leaking URL"
        )
    }
}
