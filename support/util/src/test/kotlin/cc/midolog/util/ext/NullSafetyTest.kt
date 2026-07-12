package cc.midolog.util.ext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NullSafetyTest {

    @Test
    fun `ifNull returns self when value is non-null`() {
        val value: String? = "actual"
        val result = value.ifNull { "fallback" }
        assertEquals("actual", result)
    }

    @Test
    fun `ifNull returns fallback when value is null`() {
        val value: String? = null
        val result = value.ifNull { "fallback" }
        assertEquals("fallback", result)
    }

    @Test
    fun `ifNull does not call fallback when value is non-null`() {
        var fallbackCalled = false
        val value: String? = "actual"
        val result = value.ifNull {
            fallbackCalled = true
            "fallback"
        }
        assertEquals("actual", result)
        assertEquals(false, fallbackCalled)
    }

    @Test
    fun `ifNull calls fallback only when value is null`() {
        var fallbackCalled = false
        val value: String? = null
        val result = value.ifNull {
            fallbackCalled = true
            "fallback"
        }
        assertEquals("fallback", result)
        assertEquals(true, fallbackCalled)
    }

    @Test
    fun `requireField returns non-null value when value is non-null`() {
        val userId: Long? = 42L
        val result = userId.requireField("userId")
        assertEquals(42L, result)
    }

    @Test
    fun `requireField throws IllegalArgumentException with field name when value is null`() {
        val userId: Long? = null
        val exception = assertThrows(IllegalArgumentException::class.java) {
            userId.requireField("userId")
        }
        assertEquals("userId must not be null", exception.message)
    }

    @Test
    fun `requireField throws exception with custom field name`() {
        val customField: String? = null
        val exception = assertThrows(IllegalArgumentException::class.java) {
            customField.requireField("customField")
        }
        assertEquals("customField must not be null", exception.message)
    }

    @Test
    fun `requireField works with different nullable types`() {
        val intValue: Int? = 123
        val stringValue: String? = "test"
        val boolValue: Boolean? = true

        assertEquals(123, intValue.requireField("intValue"))
        assertEquals("test", stringValue.requireField("stringValue"))
        assertEquals(true, boolValue.requireField("boolValue"))
    }

    @Test
    fun `requireField throws for all nullable types when null`() {
        val intValue: Int? = null
        val stringValue: String? = null
        val boolValue: Boolean? = null

        assertThrows(IllegalArgumentException::class.java) {
            intValue.requireField("intValue")
        }
        assertThrows(IllegalArgumentException::class.java) {
            stringValue.requireField("stringValue")
        }
        assertThrows(IllegalArgumentException::class.java) {
            boolValue.requireField("boolValue")
        }
    }
}
