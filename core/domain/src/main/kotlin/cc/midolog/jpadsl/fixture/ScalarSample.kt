package cc.midolog.jpadsl.fixture

/**
 * JPA DSL 생성기 검증용 픽스처.
 * 
 * 도메인 모델이 아니다.
 */
data class ScalarSample(
    val id: String,
    val displayName: String,
    val nickname: String?,
    val status: ScalarSampleStatus,
    val code: ScalarSampleCode,
)

enum class ScalarSampleStatus {
    ACTIVE,
    DISABLED,
}

@JvmInline
value class ScalarSampleCode(val value: String)
