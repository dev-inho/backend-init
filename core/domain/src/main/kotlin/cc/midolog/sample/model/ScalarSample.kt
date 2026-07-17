package cc.midolog.sample.model

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
