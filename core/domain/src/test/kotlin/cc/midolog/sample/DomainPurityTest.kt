package cc.midolog.sample

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 도메인 모듈의 순수성을 보장하는 아키텍처 가드 테스트.
 *
 * core:domain 모듈은 Spring, JPA, MyBatis, QueryDSL 등 외부 프레임워크나 ORM 라이브러리에
 * 결합되지 않아야 한다. 소스 파일에서 주석(Kotlin 중첩 블록 주석 포함)과 문자열 리터럴(일반 및 여러 줄 문자열)을
 * 구문 분석 단계에서 안전하게 제외한 후 실제 import 구문 및 인라인 어노테이션·FQN 의존성을 검사한다.
 * 이를 통해 주석이나 문자열에 설명용 예시로 기술된 토큰의 오탐을 방지하면서도 실제 프레임워크 의존성은 엄격히 차단한다.
 */
class DomainPurityTest {

    companion object {
        /**
         * 도메인 계층에서 사용이 금지된 외부 프레임워크 및 ORM 패키지 접두사 목록.
         */
        val FORBIDDEN_IMPORT_PREFIXES = listOf(
            "org.springframework",
            "jakarta.persistence",
            "javax.persistence",
            "org.mybatis",
            "org.apache.ibatis",
            "com.querydsl",
            "com.mysema.query",
            "org.jetbrains.exposed",
            "com.google.devtools.ksp",
            "org.hibernate",
        )

        /**
         * 주석 및 문자열을 제외한 순수 코드 본문에서 금지된 인프라/프레임워크 어노테이션 목록.
         */
        val FORBIDDEN_ANNOTATIONS = listOf(
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

        private val IMPORT_REGEX = Regex("""^\s*import\s+([^\s;]+)(?:\s+as\s+([^\s;]+))?""", RegexOption.MULTILINE)
        private val ANNOTATION_REGEX = Regex("""(@(?:Entity|Table|Id|Column|MappedSuperclass|Embeddable|Repository|Component|Service|DomainEntity|GenerateJpa))\b""")

        /**
         * Kotlin 소스 코드에서 주석과 문자열 리터럴을 제거하고 줄바꿈 구조를 유지한 순수 코드를 반환한다.
         * Kotlin 특유의 중첩 블록 주석(nested block comment)과 여러 줄 문자열(triple-quoted string),
         * 이스케이프가 포함된 일반 문자열 및 문자 리터럴을 유한 상태 기계로 정확하게 처리한다.
         */
        fun stripCommentsAndStrings(source: String): String {
            val sb = StringBuilder(source.length)
            val len = source.length
            var i = 0

            var commentDepth = 0
            var inLineComment = false
            var inMultiLineString = false
            var inSingleLineString = false
            var inCharLiteral = false

            while (i < len) {
                val c = source[i]
                val next = if (i + 1 < len) source[i + 1] else '\u0000'
                val next2 = if (i + 2 < len) source[i + 2] else '\u0000'

                if (inLineComment) {
                    if (c == '\n') {
                        inLineComment = false
                        sb.append('\n')
                    } else {
                        sb.append(' ')
                    }
                    i++
                } else if (commentDepth > 0) {
                    if (c == '/' && next == '*') {
                        commentDepth++
                        sb.append("  ")
                        i += 2
                    } else if (c == '*' && next == '/') {
                        commentDepth--
                        sb.append("  ")
                        i += 2
                    } else if (c == '\n') {
                        sb.append('\n')
                        i++
                    } else {
                        sb.append(' ')
                        i++
                    }
                } else if (inMultiLineString) {
                    if (c == '"' && next == '"' && next2 == '"') {
                        inMultiLineString = false
                        sb.append("   ")
                        i += 3
                    } else if (c == '\n') {
                        sb.append('\n')
                        i++
                    } else {
                        sb.append(' ')
                        i++
                    }
                } else if (inSingleLineString) {
                    if (c == '\\' && next != '\u0000') {
                        sb.append("  ")
                        i += 2
                    } else if (c == '"') {
                        inSingleLineString = false
                        sb.append(' ')
                        i++
                    } else if (c == '\n') {
                        inSingleLineString = false
                        sb.append('\n')
                        i++
                    } else {
                        sb.append(' ')
                        i++
                    }
                } else if (inCharLiteral) {
                    if (c == '\\' && next != '\u0000') {
                        sb.append("  ")
                        i += 2
                    } else if (c == '\'') {
                        inCharLiteral = false
                        sb.append(' ')
                        i++
                    } else if (c == '\n') {
                        inCharLiteral = false
                        sb.append('\n')
                        i++
                    } else {
                        sb.append(' ')
                        i++
                    }
                } else {
                    // 일반 코드 상태
                    if (c == '/' && next == '/') {
                        inLineComment = true
                        sb.append("  ")
                        i += 2
                    } else if (c == '/' && next == '*') {
                        commentDepth = 1
                        sb.append("  ")
                        i += 2
                    } else if (c == '"' && next == '"' && next2 == '"') {
                        inMultiLineString = true
                        sb.append("   ")
                        i += 3
                    } else if (c == '"') {
                        inSingleLineString = true
                        sb.append(' ')
                        i++
                    } else if (c == '\'') {
                        inCharLiteral = true
                        sb.append(' ')
                        i++
                    } else {
                        sb.append(c)
                        i++
                    }
                }
            }

            return sb.toString()
        }

        /**
         * 주어진 import 경로가 금지된 프레임워크 패키지인지 판정한다.
         * 패키지 접두사 뒤에 마침표(.)가 오거나 정확히 일치하는 경우만 차단하며,
         * 접두사가 유사한 정상 식별자(예: org.springframeworkfake)는 허용한다.
         */
        fun isForbiddenImport(importPath: String): Boolean {
            val cleanPath = importPath.removeSuffix(".*")
            return FORBIDDEN_IMPORT_PREFIXES.any { prefix ->
                cleanPath == prefix || cleanPath.startsWith("$prefix.")
            }
        }

        /**
         * 소스 코드에서 주석과 문자열을 제거한 후 금지된 import 및 인라인 참조 위반 사항을 검출한다.
         */
        fun checkPurityViolations(source: String, filePath: String = "source.kt"): List<String> {
            val stripped = stripCommentsAndStrings(source)
            val violations = mutableListOf<String>()

            // 1. import 구문 검사
            IMPORT_REGEX.findAll(stripped).forEach { match ->
                val fullImportPath = match.groupValues[1]
                val alias = match.groupValues[2]
                if (isForbiddenImport(fullImportPath)) {
                    val aliasDesc = if (alias.isNotBlank()) " (aliased as $alias)" else ""
                    violations.add("$filePath: forbidden import '$fullImportPath'$aliasDesc")
                }
            }

            // 2. import 라인을 제외한 본문 코드에서 인라인 FQN 및 금지 어노테이션 검사
            val linesWithoutImports = stripped.lines().map { line ->
                if (line.trimStart().startsWith("import ")) "" else line
            }

            linesWithoutImports.forEachIndexed { index, line ->
                // 금지 어노테이션 검사
                ANNOTATION_REGEX.findAll(line).forEach { match ->
                    val annotation = match.groupValues[1]
                    violations.add("$filePath:${index + 1}: forbidden annotation '$annotation'")
                }

                // 인라인 FQN 직접 사용 검사
                FORBIDDEN_IMPORT_PREFIXES.forEach { prefix ->
                    val fqnPattern = Regex("""\b${Regex.escape(prefix)}\.""")
                    if (fqnPattern.containsMatchIn(line)) {
                        violations.add("$filePath:${index + 1}: inline usage of forbidden package '$prefix'")
                    }
                }
            }

            return violations
        }
    }

    @Test
    fun `domain source stays free of Spring JPA and MyBatis dependencies`() {
        val sourceRoot = Path.of("src/main/kotlin")
        val userSourceRoot = sourceRoot.resolve("cc/midolog/user")
        val fileSourceRoot = sourceRoot.resolve("cc/midolog/file")

        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { path ->
                    val text = path.readText()
                    checkPurityViolations(text, path.toString()).stream()
                }
                .toList()
        }

        assertTrue(Files.isDirectory(userSourceRoot), "user domain package should exist")
        assertTrue(Files.isDirectory(fileSourceRoot), "file domain package should exist")
        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    @Test
    fun `allows forbidden tokens in nested comments single-line comments and multiline strings`() {
        val codeWithCommentsAndStrings = listOf(
            "package cc.midolog.domain.sample",
            "",
            "// Single line comment mentioning org.springframework.stereotype.Component and @Entity",
            "/*",
            "    Block comment mentioning " + "jakarta.persistence.Table",
            "    /*",
            "        Nested block comment mentioning " + "org.mybatis.dynamic.sql.* and " + "com.querydsl.core.types.Predicate",
            "    */",
            "    Back in outer block with @Service and @Repository",
            "*/",
            "",
            "class DocumentedDomain {",
            "    val doc: String = \"\"\"",
            "        Example configuration:",
            "        " + "import org.springframework.context.annotation.Configuration",
            "        " + "import " + "jakarta.persistence.Entity",
            "        @Entity",
            "        @Table(name = \"test\")",
            "    \"\"\".trimIndent()",
            "",
            "    val singleString: String = \"org.springframework.beans.factory.annotation.Autowired\"",
            "    val charSample: Char = 'a'",
            "}",
        ).joinToString("\n")

        val violations = checkPurityViolations(codeWithCommentsAndStrings, "DocumentedDomain.kt")
        assertTrue(violations.isEmpty(), "Comments and strings must not trigger false positives: $violations")
    }

    @Test
    fun `detects forbidden imports including wildcard and import alias`() {
        val invalidCode = listOf(
            "package cc.midolog.domain.sample",
            "",
            "import org.springframework.stereotype.Service",
            "import org.springframework.beans.factory.annotation.Autowired as SpringAutowired",
            "import " + "jakarta.persistence.*",
            "import " + "org.mybatis.dynamic.sql.*",
            "import " + "com.querydsl.core.types.Predicate",
            "import org.jetbrains.exposed.sql.Table",
            "",
            "class InvalidSample",
        ).joinToString("\n")

        val violations = checkPurityViolations(invalidCode, "InvalidSample.kt")
        assertEquals(6, violations.size, "Must detect all 6 forbidden imports")
        assertTrue(violations.any { it.contains("org.springframework.stereotype.Service") })
        assertTrue(violations.any { it.contains("org.springframework.beans.factory.annotation.Autowired") && it.contains("SpringAutowired") })
        assertTrue(violations.any { it.contains("jakarta.persistence.*") })
        assertTrue(violations.any { it.contains("org.mybatis.dynamic.sql.*") })
        assertTrue(violations.any { it.contains("com.querydsl.core.types.Predicate") })
        assertTrue(violations.any { it.contains("org.jetbrains.exposed.sql.Table") })
    }

    @Test
    fun `does not flag legitimate imports with similar package prefix`() {
        val legitimateCode = """
            package cc.midolog.domain.sample

            import org.springframeworkfake.Something
            import org.mybatissomething.Foo
            import com.querydsl_other.Bar
            import jakarta.persistencedummy.Baz
            import java.time.Instant

            class LegitimateSample(val createdAt: Instant)
        """.trimIndent()

        val violations = checkPurityViolations(legitimateCode, "LegitimateSample.kt")
        assertTrue(violations.isEmpty(), "Legitimate imports with similar prefix must not be flagged: $violations")
    }

    @Test
    fun `detects inline forbidden annotations and forbidden FQN in code body`() {
        val codeWithInlineViolations = """
            package cc.midolog.domain.sample

            @Entity
            @Table(name = "sample")
            class EntitySample {
                @Id
                val id: String = "1"

                fun useSpring() {
                    val context: org.springframework.context.ApplicationContext? = null
                }
            }
        """.trimIndent()

        val violations = checkPurityViolations(codeWithInlineViolations, "EntitySample.kt")
        assertFalse(violations.isEmpty(), "Must detect inline annotations and FQN usages")
        assertTrue(violations.any { it.contains("forbidden annotation '@Entity'") })
        assertTrue(violations.any { it.contains("forbidden annotation '@Table'") })
        assertTrue(violations.any { it.contains("forbidden annotation '@Id'") })
        assertTrue(violations.any { it.contains("inline usage of forbidden package 'org.springframework'") })
    }
}
