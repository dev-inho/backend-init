package cc.midolog.customers.acme

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class AcmeCustomerHasNoStereotypeTest {

    @Test
    fun `customers acme module should not have any spring stereotype annotations`() {
        val srcDir = File("src/main/kotlin")
        if (!srcDir.exists()) {
            return
        }

        val stereotypeRegex = Regex(
            "(@(Component|Configuration|RestController|Controller|Service|Repository)\\b|" +
                "@org\\.springframework\\.(?:stereotype\\.(?:Component|Controller|Service|Repository)|context\\.annotation\\.Configuration|web\\.bind\\.annotation\\.RestController)\\b|" +
                "import org\\.springframework\\.(?:stereotype\\.(?:Component|Controller|Service|Repository)|context\\.annotation\\.Configuration|web\\.bind\\.annotation\\.RestController)\\b)"
        )
        val filesWithStereotypes = mutableListOf<String>()

        Files.walk(srcDir.toPath()).use { stream ->
            stream.filter { it.isRegularFile() && it.extension == "kt" }
                .forEach { path ->
                    val content = path.readText()
                    if (stereotypeRegex.containsMatchIn(content)) {
                        filesWithStereotypes.add(path.toString())
                    }
                }
        }

        assertTrue(
            filesWithStereotypes.isEmpty(),
            "Spring stereotype annotations found in customers/acme: \n" + filesWithStereotypes.joinToString("\n")
        )
    }
}
