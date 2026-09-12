package cc.midolog.gateway

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class GatewayCoreHasNoStereotypeTest {

    @Test
    fun `gateway core should not have any spring stereotype annotations`() {
        val srcDir = File("src/main/kotlin")
        if (!srcDir.exists()) {
            return
        }

        val stereotypeRegex = Regex("@(Component|Configuration|RestController|Controller|Service|Repository)\\b")
        val filesWithStereotypes = mutableListOf<String>()

        Files.walk(srcDir.toPath())
            .filter { it.isRegularFile() && it.extension == "kt" }
            .forEach { path ->
                val content = path.readText()
                if (stereotypeRegex.containsMatchIn(content)) {
                    filesWithStereotypes.add(path.toString())
                }
            }

        assertTrue(
            filesWithStereotypes.isEmpty(),
            "Spring stereotype annotations found in gateway/core: \n" + filesWithStereotypes.joinToString("\n")
        )
    }
}
