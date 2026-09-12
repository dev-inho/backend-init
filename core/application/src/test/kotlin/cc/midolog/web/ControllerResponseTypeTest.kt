package cc.midolog.web

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

class ControllerResponseTypeTest {

    @Test
    fun `컨트롤러는 도메인 모델을 직접 응답하지 않아야 한다`() {
        val webDir = File("src/main/kotlin/cc/midolog/web")
        if (!webDir.exists()) {
            println("webDir not found at ${webDir.absolutePath}, skipping or running from different root")
            return
        }
        
        val controllers = webDir.walkTopDown().filter { it.isFile && it.name.endsWith("Controller.kt") }.toList()
        
        val failures = mutableListOf<String>()
        controllers.forEach { file ->
            val content = file.readText()
            if (content.contains("ApiResponse<Sample>") || content.contains("ApiResponse<User>")) {
                failures.add("${file.name} exposes domain model in ApiResponse")
            }
        }
        
        assertFalse(failures.isNotEmpty(), "Found domain model leaks:\n${failures.joinToString("\n")}")
    }
}
