package cc.midolog.util.mask

/**
 * Sensitive data masking helpers with conservative exposure rules.
 */
object Masking {

    /**
     * Masks an email address by keeping the first local-part character and the
     * full domain. If the value is not email-shaped, masks the whole input.
     */
    fun maskEmail(email: String): String {
        if (email.isEmpty()) return email

        val atIndex = email.indexOf('@')
        if (atIndex == -1) return "*".repeat(email.length)

        val localPart = email.substring(0, atIndex)
        val domain = email.substring(atIndex)
        val maskedLocal = if (localPart.length <= 1) {
            localPart
        } else {
            localPart[0] + "*".repeat(localPart.length - 1)
        }

        return maskedLocal + domain
    }

    /**
     * Masks phone digits while preserving non-digit separators in their
     * original positions. Four digit values are already minimally revealing and
     * are left as-is; shorter digit values are fully masked.
     */
    fun maskPhone(phone: String): String = maskDigitsKeepingSeparators(phone)

    /**
     * Masks card digits while preserving non-digit separators in their original
     * positions. Four digit values are already minimally revealing and are left
     * as-is; shorter digit values are fully masked.
     */
    fun maskCard(card: String): String = maskDigitsKeepingSeparators(card)

    private fun maskDigitsKeepingSeparators(value: String): String {
        val digitCount = value.count { it.isDigit() }
        if (digitCount == 0) return value

        var seenDigits = 0
        return buildString(value.length) {
            for (char in value) {
                if (!char.isDigit()) {
                    append(char)
                    continue
                }

                seenDigits += 1
                val shouldReveal = digitCount == 4 || (digitCount > 4 && seenDigits > digitCount - 4)
                append(if (shouldReveal) char else '*')
            }
        }
    }
}
