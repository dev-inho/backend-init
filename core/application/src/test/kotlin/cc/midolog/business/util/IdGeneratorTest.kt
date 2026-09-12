package cc.midolog.business.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IdGeneratorTest {
    @Test
    fun `generateUlid should return 26 characters of Crockford base32`() {
        val ulid = IdGenerator.generateUlid()
        assertEquals(26, ulid.length)
        assertTrue(ulid.matches(Regex("^[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{26}$")), "Must be Crockford base32")
    }

    @Test
    fun `generateUlid should generate unique ids`() {
        val set = mutableSetOf<String>()
        for (i in 0 until 10000) {
            val ulid = IdGenerator.generateUlid()
            assertTrue(set.add(ulid), "Should not have duplicates")
        }
    }
}
