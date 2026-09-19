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
}
