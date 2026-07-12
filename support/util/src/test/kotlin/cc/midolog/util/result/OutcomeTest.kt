package cc.midolog.util.result

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutcomeTest {

    @Test
    fun `success factory creates a Success instance`() {
        val result = success(42)
        assertTrue(result is Outcome.Success)
        assertEquals(42, (result as Outcome.Success).value)
    }

    @Test
    fun `failure factory creates a Failure instance`() {
        val result = failure("error")
        assertTrue(result is Outcome.Failure)
        assertEquals("error", (result as Outcome.Failure).error)
    }

    @Test
    fun `map transforms success value`() {
        val result = success(5).map { it * 2 }
        assertEquals(10, result.getOrNull())
    }

    @Test
    fun `map passes through failure`() {
        val start: Outcome<Int, String> = failure("error")
        val result = start.map { it * 2 }
        assertEquals("error", result.failureOrNull())
    }

    @Test
    fun `mapFailure transforms failure value`() {
        val result = failure("error").mapFailure { "wrapped: $it" }
        assertEquals("wrapped: error", result.failureOrNull())
    }

    @Test
    fun `mapFailure passes through success`() {
        val result = success(42).mapFailure { "wrapped: $it" }
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `fold applies onSuccess for success`() {
        val result = success(5).fold(
            onSuccess = { it * 2 },
            onFailure = { -1 }
        )
        assertEquals(10, result)
    }

    @Test
    fun `fold applies onFailure for failure`() {
        val start: Outcome<Int, String> = failure("error")
        val result = start.fold(
            onSuccess = { it * 2 },
            onFailure = { it.length }
        )
        assertEquals(5, result)
    }

    @Test
    fun `getOrNull returns value for success`() {
        val result = success(42).getOrNull()
        assertEquals(42, result)
    }

    @Test
    fun `getOrNull returns null for failure`() {
        val start: Outcome<Int, String> = failure("error")
        val result = start.getOrNull()
        assertNull(result)
    }

    @Test
    fun `failureOrNull returns error for failure`() {
        val result = failure("error").failureOrNull()
        assertEquals("error", result)
    }

    @Test
    fun `failureOrNull returns null for success`() {
        val result = success(42).failureOrNull()
        assertNull(result)
    }

    @Test
    fun `isSuccess returns true for success`() {
        assertTrue(success(42).isSuccess())
    }

    @Test
    fun `isSuccess returns false for failure`() {
        assertFalse(failure("error").isSuccess())
    }

    @Test
    fun `isFailure returns true for failure`() {
        assertTrue(failure("error").isFailure())
    }

    @Test
    fun `isFailure returns false for success`() {
        assertFalse(success(42).isFailure())
    }

    @Test
    fun `chaining multiple map operations`() {
        val result = success(2)
            .map { it * 3 }
            .map { it + 1 }
            .map { it.toString() }
        assertEquals("7", result.getOrNull())
    }

    @Test
    fun `chaining map and mapFailure on success`() {
        val result = success(10)
            .map { it * 2 }
            .mapFailure { "error: $it" }
        assertEquals(20, result.getOrNull())
    }

    @Test
    fun `chaining map and mapFailure on failure`() {
        val start: Outcome<Int, String> = failure("failed")
        val result = start
            .map { it * 2 }
            .mapFailure { it.uppercase() }
        assertEquals("FAILED", result.failureOrNull())
    }

    @Test
    fun `success with different types`() {
        val stringResult = success("hello")
        val intResult = success(123)
        val boolResult = success(true)

        assertEquals("hello", stringResult.getOrNull())
        assertEquals(123, intResult.getOrNull())
        assertTrue(boolResult.getOrNull() ?: false)
    }

    @Test
    fun `failure with different error types`() {
        val stringError = failure("error message")
        val exceptionError = failure(IllegalArgumentException("invalid"))
        val intError = failure(404)

        assertEquals("error message", stringError.failureOrNull())
        assertEquals("invalid", (exceptionError.failureOrNull() as IllegalArgumentException?)?.message)
        assertEquals(404, intError.failureOrNull())
    }
}
