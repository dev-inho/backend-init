package cc.midolog.storage.mybatis

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * MyBatis 매핑이 기존 XML/Map 기반에서 MyBatis Dynamic SQL로 완전히 전환되었음을 보장하는 아키텍처 가드 테스트.
 *
 * storage/mybatis/src/main 내에 resultType="map" 속성, Map<String, Any?> 매퍼 반환,
 * 그리고 select/update XML 구문이 0건이어야 함을 엄격히 검증한다.
 */
class MyBatisDynamicSqlTransitionGuardTest {

    @Test
    fun `storage-mybatis src-main XML must contain zero occurrences of resultType map attribute`() {
        val srcMain = findSrcMain()
        val pattern = Regex("""resultType\s*=\s*["']map["']""", RegexOption.IGNORE_CASE)
        val violations = mutableListOf<String>()

        srcMain.walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (pattern.containsMatchIn(line)) {
                    violations.add("${file.relativeTo(srcMain)}:${index + 1}: ${line.trim()}")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found resultType=\"map\" attribute in storage/mybatis/src/main XML files (expected 0):\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun `storage-mybatis src-main must contain zero mapper return types of Map`() {
        val srcMain = findSrcMain()
        val pattern = Regex("""\)\s*:\s*(List<)?Map<String,\s*Any\??>""")
        val violations = mutableListOf<String>()

        srcMain.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (pattern.containsMatchIn(line)) {
                    violations.add("${file.relativeTo(srcMain)}:${index + 1}: ${line.trim()}")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found Map<String, Any?> mapper return type in storage/mybatis/src/main (expected 0):\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun `storage-mybatis src-main XML must contain zero select and update elements`() {
        val srcMain = findSrcMain()
        val selectPattern = Regex("""<select\b""")
        val updatePattern = Regex("""<update\b""")
        val violations = mutableListOf<String>()

        srcMain.walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (selectPattern.containsMatchIn(line)) {
                    violations.add("<select> found in ${file.relativeTo(srcMain)}:${index + 1}: ${line.trim()}")
                }
                if (updatePattern.containsMatchIn(line)) {
                    violations.add("<update> found in ${file.relativeTo(srcMain)}:${index + 1}: ${line.trim()}")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found <select> or <update> XML elements in storage/mybatis/src/main (expected 0):\n" + violations.joinToString("\n")
        )
    }

    private fun findSrcMain(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            val candidate = File(current, "storage/mybatis/src/main")
            if (candidate.exists()) {
                return candidate
            }
            current = current.parentFile
        }
        return File("storage/mybatis/src/main").canonicalFile
    }
}
