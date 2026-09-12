package cc.midolog.util.mask

/**
 * 개인정보 및 민감 데이터(이메일, 전화번호, 카드번호)를 보수적 노출 원칙에 따라 마스킹하는 순수 유틸리티.
 *
 * 로그나 외부 응답을 통해 평문 PII가 유출되는 것을 방지하기 위해 사용된다(support:logging의 MaskingSupport 등에서 호출).
 */
object Masking {

    /**
     * 이메일 주소의 로컬 파트 첫 글자와 도메인 전체만 보존하고 나머지를 `*`로 마스킹한다.
     *
     * `@` 기호가 없는 비정상 형식 문자열은 전체를 `*`로 마스킹하며, 빈 문자열은 그대로 반환한다.
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
     * 전화번호의 비숫자 구분자(하이픈, 공백 등) 위치를 유지하면서 뒤 4자리를 제외한 앞선 숫자들을 `*`로 마스킹한다.
     *
     * 총 숫자가 4자리면 최소 식별 정보로 보아 그대로 보존하고, 4자리 미만이면 숫자를 모두 마스킹한다.
     */
    fun maskPhone(phone: String): String = maskDigitsKeepingSeparators(phone)

    /**
     * 카드번호의 비숫자 구분자 위치를 유지하면서 뒤 4자리를 제외한 앞선 숫자들을 `*`로 마스킹한다.
     *
     * 총 숫자가 4자리면 최소 식별 정보로 보아 그대로 보존하고, 4자리 미만이면 숫자를 모두 마스킹한다.
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
