package cc.midolog.buildlogic.jpadsl

import kotlin.test.Test
import kotlin.test.assertEquals

class GenerateJpaDslSourcesTaskTest {
    @Test
    fun `defaultValueForKotlinType supports java time Instant`() {
        val result = defaultValueForKotlinType("java.time.Instant")
        assertEquals("java.time.Instant.EPOCH", result)
    }
}
