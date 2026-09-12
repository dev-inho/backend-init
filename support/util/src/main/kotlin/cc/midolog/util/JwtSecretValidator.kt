package cc.midolog.util

/**
 * JWT 서명 시크릿 검증기 (순수 Kotlin, Spring/외부 의존성 없음).
 *
 * Application·Gateway 양쪽 모듈이 JWT 키를 만들기 직전에 호출해
 * 알려진 기본값·짧은 시크릿으로 조용히 기동하는 것을 막는다(fail-fast).
 * 빈 값(blank), 레포에 노출된 적 있는 알려진 기본 플레이스홀더, UTF-8 인코딩 기준
 * [MIN_BYTE_LENGTH]바이트 미만의 약한 시크릿을 모두 거부한다.
 */
object JwtSecretValidator {
    /** HS256 서명 키 최소 길이(UTF-8 바이트). RFC 7518이 요구하는 256비트 하한. */
    const val MIN_BYTE_LENGTH = 32

    /** 과거 application.yml/.env.example에 하드코딩되어 노출됐던 기본 시크릿. */
    private val KNOWN_DEFAULT_SECRETS = setOf(
        "change-me-please-use-a-strong-32byte-min-secret-key-0123456789",
    )

    /**
     * [secret]이 JWT 서명 키로 사용해도 안전한지 검증하고, 유효하면 그대로 반환한다.
     * 규칙을 위반하면 [IllegalStateException]을 던져 애플리케이션 기동을 즉시 중단시킨다.
     */
    fun validate(secret: String): String {
        check(secret.isNotBlank()) {
            "jwt.secret must not be blank"
        }
        check(secret !in KNOWN_DEFAULT_SECRETS) {
            "jwt.secret must not use the known default placeholder value; " +
                "set a strong random JWT_SECRET (e.g. openssl rand -base64 48)"
        }
        val byteLength = secret.toByteArray(Charsets.UTF_8).size
        check(byteLength >= MIN_BYTE_LENGTH) {
            "jwt.secret must be at least $MIN_BYTE_LENGTH bytes (UTF-8) but was $byteLength bytes"
        }
        return secret
    }
}
