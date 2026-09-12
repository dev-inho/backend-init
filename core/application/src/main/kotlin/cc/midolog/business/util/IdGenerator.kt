package cc.midolog.business.util

import java.security.SecureRandom

object IdGenerator {
    private val ENCODING_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    private val secureRandom = SecureRandom()

    fun generateUlid(): String {
        val timestamp = System.currentTimeMillis()
        val randomBytes = ByteArray(10)
        secureRandom.nextBytes(randomBytes)
        
        val chars = CharArray(26)
        
        var t = timestamp
        for (i in 9 downTo 0) {
            chars[i] = ENCODING_CHARS[(t and 31).toInt()]
            t = t ushr 5
        }
        
        chars[10] = ENCODING_CHARS[(randomBytes[0].toInt() and 0xFF ushr 3) and 31]
        chars[11] = ENCODING_CHARS[((randomBytes[0].toInt() and 0x07 shl 2) or (randomBytes[1].toInt() and 0xFF ushr 6)) and 31]
        chars[12] = ENCODING_CHARS[(randomBytes[1].toInt() and 0xFF ushr 1) and 31]
        chars[13] = ENCODING_CHARS[((randomBytes[1].toInt() and 0x01 shl 4) or (randomBytes[2].toInt() and 0xFF ushr 4)) and 31]
        chars[14] = ENCODING_CHARS[((randomBytes[2].toInt() and 0x0F shl 1) or (randomBytes[3].toInt() and 0xFF ushr 7)) and 31]
        chars[15] = ENCODING_CHARS[(randomBytes[3].toInt() and 0xFF ushr 2) and 31]
        chars[16] = ENCODING_CHARS[((randomBytes[3].toInt() and 0x03 shl 3) or (randomBytes[4].toInt() and 0xFF ushr 5)) and 31]
        chars[17] = ENCODING_CHARS[(randomBytes[4].toInt() and 0x1F)]
        
        chars[18] = ENCODING_CHARS[(randomBytes[5].toInt() and 0xFF ushr 3) and 31]
        chars[19] = ENCODING_CHARS[((randomBytes[5].toInt() and 0x07 shl 2) or (randomBytes[6].toInt() and 0xFF ushr 6)) and 31]
        chars[20] = ENCODING_CHARS[(randomBytes[6].toInt() and 0xFF ushr 1) and 31]
        chars[21] = ENCODING_CHARS[((randomBytes[6].toInt() and 0x01 shl 4) or (randomBytes[7].toInt() and 0xFF ushr 4)) and 31]
        chars[22] = ENCODING_CHARS[((randomBytes[7].toInt() and 0x0F shl 1) or (randomBytes[8].toInt() and 0xFF ushr 7)) and 31]
        chars[23] = ENCODING_CHARS[(randomBytes[8].toInt() and 0xFF ushr 2) and 31]
        chars[24] = ENCODING_CHARS[((randomBytes[8].toInt() and 0x03 shl 3) or (randomBytes[9].toInt() and 0xFF ushr 5)) and 31]
        chars[25] = ENCODING_CHARS[(randomBytes[9].toInt() and 0x1F)]

        return String(chars)
    }
}
