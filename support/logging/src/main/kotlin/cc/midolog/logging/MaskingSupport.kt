package cc.midolog.logging

import cc.midolog.util.mask.Masking

/**
 * 로그 메시지 텍스트에 임베드된 이메일/전화번호/카드번호 후보를 정규식으로 탐지해
 * util Masking(maskEmail/maskPhone/maskCard)으로 치환하는 순수 로직.
 *
 * 보안 원칙은 fail-closed다 — 미탐(탐지 실패)이 곧 평문 유출이므로, 흔한 구분자
 * 변형(하이픈/공백/점/슬래시)과 국제 전화 접두(+82), Amex 4-6-5 그룹까지 폭넓게
 * 탐지한다. 카드 패턴을 전화 패턴보다 먼저 처리해 자릿수가 겹치는 숫자열이
 * 카드로 우선 인식되도록 한다. 과탐(비-PII 숫자열을 마스킹)은 fail-closed 방향의
 * 허용 가능한 트레이드오프로 본다.
 */
object MaskingSupport {

    /**
     * 이메일 패턴. 소유(possessive) 수량자(++)로 백트래킹을 제거해 ReDoS를 막는다 —
     * 각 라벨과 로컬 파트가 한 번 소비되면 되돌아가지 않으므로 매칭이 입력 길이에 선형이다.
     * 도메인은 "라벨.(1회 이상) + TLD" 형태로 구성한다.
     */
    private val EMAIL_PATTERN =
        Regex("""[A-Za-z0-9._%+-]++@(?:[A-Za-z0-9-]++\.)++[A-Za-z]{2,}""")

    /**
     * 카드번호 후보:
     * - 4-4-4-(1~4) 그룹, 구분자 하이픈/공백/점/슬래시
     * - Amex 4-6-5 그룹
     * - 구분자 없는 13~19자리 연속 숫자
     */
    private val CARD_PATTERN =
        Regex(
            """\b(?:\d{4}[-. /]\d{4}[-. /]\d{4}[-. /]\d{1,4}|""" +
                """\d{4}[-. /]\d{6}[-. /]\d{5}|""" +
                """\d{13,19})\b""",
        )

    /**
     * 전화번호 후보:
     * - 국내 휴대전화 01x, 구분자 하이픈/공백/점 또는 없음
     * - 국제 접두 +82 형태(선행 0 제거)
     * - 지역번호 유선(구분자 필수로 오탐 억제)
     */
    private val PHONE_PATTERN =
        Regex(
            """\b01[0-9][-. ]?\d{3,4}[-. ]?\d{4}\b|""" +
                """(?<!\d)\+82[-. ]?1[0-9][-. ]?\d{3,4}[-. ]?\d{4}(?!\d)|""" +
                """\b0\d{1,2}[-. ]\d{3,4}[-. ]\d{4}\b""",
        )

    /**
     * 텍스트 내 email → card → phone 순서로 후보를 탐지·마스킹한다.
     * 카드를 전화보다 먼저 처리해 자릿수가 겹치는 숫자열의 오탐을 줄인다.
     */
    fun maskText(text: String): String {
        var masked = EMAIL_PATTERN.replace(text) { Masking.maskEmail(it.value) }
        masked = maskCardCandidates(masked)
        masked = PHONE_PATTERN.replace(masked) { Masking.maskPhone(it.value) }
        return masked
    }

    private fun maskCardCandidates(text: String): String =
        CARD_PATTERN.replace(text) { match ->
            val digitCount = match.value.count { it.isDigit() }
            if (digitCount in 13..19) Masking.maskCard(match.value) else match.value
        }
}
