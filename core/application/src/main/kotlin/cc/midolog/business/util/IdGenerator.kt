package cc.midolog.business.util

import java.security.SecureRandom

object IdGenerator {
    private val ENCODING_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    private val secureRandom = SecureRandom()

    fun generateUlid(): String {
        val timestamp = System.currentTimeMillis()
        val randomBytes = ByteArray(10)
        secureRandom.nextBytes(randomBytes)
        return encode(timestamp, randomBytes)
    }

    internal fun encode(timestamp: Long, randomBytes: ByteArray): String {
        require(randomBytes.size == 10) { "randomBytes must be 10 bytes" }
        val chars = CharArray(26)

        var t = timestamp
        for (i in 9 downTo 0) {
            chars[i] = ENCODING_CHARS[(t and 31).toInt()]
            t = t ushr 5
        }

        val b0 = randomBytes[0].toInt() and 0xFF
        val b1 = randomBytes[1].toInt() and 0xFF
        val b2 = randomBytes[2].toInt() and 0xFF
        val b3 = randomBytes[3].toInt() and 0xFF
        val b4 = randomBytes[4].toInt() and 0xFF
        val b5 = randomBytes[5].toInt() and 0xFF
        val b6 = randomBytes[6].toInt() and 0xFF
        val b7 = randomBytes[7].toInt() and 0xFF
        val b8 = randomBytes[8].toInt() and 0xFF
        val b9 = randomBytes[9].toInt() and 0xFF

        chars[10] = ENCODING_CHARS[(b0 ushr 3) and 31]
        chars[11] = ENCODING_CHARS[(((b0 and 0x07) shl 2) or (b1 ushr 6)) and 31]
        chars[12] = ENCODING_CHARS[(b1 ushr 1) and 31]
        chars[13] = ENCODING_CHARS[(((b1 and 0x01) shl 4) or (b2 ushr 4)) and 31]
        chars[14] = ENCODING_CHARS[(((b2 and 0x0F) shl 1) or (b3 ushr 7)) and 31]
        chars[15] = ENCODING_CHARS[(b3 ushr 2) and 31]
        chars[16] = ENCODING_CHARS[(((b3 and 0x03) shl 3) or (b4 ushr 5)) and 31]
        chars[17] = ENCODING_CHARS[b4 and 0x1F]

        chars[18] = ENCODING_CHARS[(b5 ushr 3) and 31]
        chars[19] = ENCODING_CHARS[(((b5 and 0x07) shl 2) or (b6 ushr 6)) and 31]
        chars[20] = ENCODING_CHARS[(b6 ushr 1) and 31]
        chars[21] = ENCODING_CHARS[(((b6 and 0x01) shl 4) or (b7 ushr 4)) and 31]
        chars[22] = ENCODING_CHARS[(((b7 and 0x0F) shl 1) or (b8 ushr 7)) and 31]
        chars[23] = ENCODING_CHARS[(b8 ushr 2) and 31]
        chars[24] = ENCODING_CHARS[(((b8 and 0x03) shl 3) or (b9 ushr 5)) and 31]
        chars[25] = ENCODING_CHARS[b9 and 0x1F]

        return String(chars)
    }
}
