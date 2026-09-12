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

    @Test
    fun `encode should generate correct 80-bit Crockford base32 with known vectors`() {
        val timestamp = 1469918176385L // 01ARYZ6S41

        // All zeros
        val zeros = ByteArray(10)
        assertEquals("01ARYZ6S410000000000000000", IdGenerator.encode(timestamp, zeros))

        // All ones (255)
        val ones = ByteArray(10) { 255.toByte() }
        assertEquals("01ARYZ6S41ZZZZZZZZZZZZZZZZ", IdGenerator.encode(timestamp, ones))

        // Known vector
        val knownBytes = byteArrayOf(
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte(), 0x99.toByte(), 0xAA.toByte()
        )
        assertEquals("01ARYZ6S4124H36H2NCSVRH6DA", IdGenerator.encode(timestamp, knownBytes))

        // Bug case validation
        val buggyVector = ByteArray(10)
        buggyVector[0] = 0x80.toByte()
        val result = IdGenerator.encode(timestamp, buggyVector)
        assertTrue(result.startsWith("01ARYZ6S41G"), "Must correctly shift MSB byte without infix precedence bug. Result: $result")
    }
}
