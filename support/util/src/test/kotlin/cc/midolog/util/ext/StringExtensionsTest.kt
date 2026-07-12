package cc.midolog.util.ext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StringExtensionsTest {

    @Test
    fun `trimToNull returns null for null string`() {
        val result: String? = null
        assertEquals(null, result.trimToNull())
    }

    @Test
    fun `trimToNull returns null for blank string`() {
        assertEquals(null, "   ".trimToNull())
    }

    @Test
    fun `trimToNull returns null for tab and newline only`() {
        assertEquals(null, "\t\n".trimToNull())
    }

    @Test
    fun `trimToNull returns trimmed string for non-blank string`() {
        assertEquals("hello", "  hello  ".trimToNull())
    }

    @Test
    fun `trimToNull removes leading and trailing whitespace`() {
        assertEquals("test", "\t test \n".trimToNull())
    }

    @Test
    fun `abbreviate returns original string when length is within limit`() {
        assertEquals("hello", "hello".abbreviate(10))
    }

    @Test
    fun `abbreviate returns original string when length equals maxLength`() {
        assertEquals("hello", "hello".abbreviate(5))
    }

    @Test
    fun `abbreviate truncates and appends ellipsis when exceeding maxLength`() {
        assertEquals("hel...", "hello world".abbreviate(6))
    }

    @Test
    fun `abbreviate maintains exact maxLength after truncation`() {
        val result = "hello world".abbreviate(6)
        assertEquals(6, result.length)
    }

    @Test
    fun `abbreviate uses custom ellipsis`() {
        assertEquals("hell***", "hello world".abbreviate(7, "***"))
    }

    @Test
    fun `abbreviate with custom ellipsis maintains exact maxLength`() {
        val result = "hello world".abbreviate(7, "***")
        assertEquals(7, result.length)
    }

    @Test
    fun `abbreviate throws exception when maxLength less than ellipsis length`() {
        assertThrows(IllegalArgumentException::class.java) {
            "hello".abbreviate(2)
        }
    }

    @Test
    fun `abbreviate throws exception when maxLength less than custom ellipsis length`() {
        assertThrows(IllegalArgumentException::class.java) {
            "hello".abbreviate(2, "****")
        }
    }

    @Test
    fun `abbreviate with maxLength equal to ellipsis length returns only ellipsis`() {
        assertEquals("...", "hello world".abbreviate(3))
    }

    @Test
    fun `abbreviate with single character and ellipsis throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            "hello".abbreviate(1)
        }
    }
}
