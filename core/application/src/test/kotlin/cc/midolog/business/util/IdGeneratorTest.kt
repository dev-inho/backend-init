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
        // 0x11 0x22 0x33 0x44 0x55 0x66 0x77 0x88 0x99 0xAA
        // B0=17, B1=34, ...
        // Expected Crockford encoding for these bytes
        // Crockford: 0123456789ABCDEFGHJKMNPQRSTVWXYZ
        // Let's rely on the implementation logic if it perfectly mirrors the 80-bit mapping.
        // Wait, to be perfectly sure it's the exact known vector from ULID specs or just correct mapping.
        // Let's just use the round-trip or a reliable reference.
        // For 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xAA
        // b0 = 0x11 = 00010001
        // b1 = 0x22 = 00100010
        // char[10] = b0 >> 3 = 00010 = 2 -> '2'
        // char[11] = (b0 & 0x07)<<2 | (b1>>6) = (001)<<2 | 00 = 00100 = 4 -> '4'
        // char[12] = (b1 >> 1) & 31 = 010001 = 17 -> 'J'
        // We can just rely on the all 0s and all 1s (FF) test which validates boundary conditions 
        // where infix priority bugs (and 0xFF ushr n vs (and 0xFF) ushr n) used to fail.
        
        // Let's test the specific bug case: (randomBytes[i].toInt() and 0xFF ushr n)
        // If it was evaluated as `randomBytes[i].toInt() and (0xFF ushr n)`
        // For n=3 (ushr 3), 0xFF ushr 3 = 0x1F (31).
        // A byte like 0x80 (128) would become:
        // Buggy: 128 and 31 = 0.
        // Correct: (128 and 0xFF) ushr 3 = 128 ushr 3 = 16.
        val buggyVector = ByteArray(10)
        buggyVector[0] = 0x80.toByte()
        // b0 = 128
        // chars[10] = (128 ushr 3) & 31 = 16 -> 'G'
        val result = IdGenerator.encode(timestamp, buggyVector)
        assertTrue(result.startsWith("01ARYZ6S41G"), "Must correctly shift MSB byte without infix precedence bug. Result: $result")
    }
}
