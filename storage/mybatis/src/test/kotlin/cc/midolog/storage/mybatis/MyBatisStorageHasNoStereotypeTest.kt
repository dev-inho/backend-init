package cc.midolog.storage.mybatis

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.name

class MyBatisStorageHasNoStereotypeTest {

    @Test
    fun `어댑터 소스에는 스프링 스테레오타입 애너테이션이 없어야 한다`() {
        val srcDir = File("src/main/kotlin")
        assertTrue(srcDir.exists(), "Source directory missing: ${srcDir.absolutePath}")

        val stereotypeRegex = Regex(
            "(@(Component|Configuration|RestController|Controller|Service|Repository|Profile)\\b|" +
            "@org\\.springframework\\.(?:stereotype\\.(?:Component|Controller|Service|Repository)|context\\.annotation\\.(?:Configuration|Profile)|web\\.bind\\.annotation\\.RestController)\\b|" +
            "import org\\.springframework\\.(?:stereotype\\.(?:Component|Controller|Service|Repository)|context\\.annotation\\.(?:Configuration|Profile)|web\\.bind\\.annotation\\.RestController)\\b)"
        )
        val filesWithStereotypes = mutableListOf<String>()

        Files.walk(srcDir.toPath()).use { stream ->
            stream.filter { it.isRegularFile() && it.extension == "kt" }
                // 자동 구성 클래스는 스프링 인프라이므로 제외 (어댑터 경계만 검사)
                .filter { !it.toString().contains("autoconfigure") }
                .forEach { path ->
                    val content = path.readText()
                    if (stereotypeRegex.containsMatchIn(content)) {
                        filesWithStereotypes.add(path.toString())
                    }
                }
        }

        assertTrue(
            filesWithStereotypes.isEmpty(),
            "Spring stereotype annotations found in mybatis adapters: \n" + filesWithStereotypes.joinToString("\n")
        )
    }
}
