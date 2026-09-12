package cc.midolog.jpadsl.fixture

/**
 * JPA DSL 생성기 검증용 픽스처이며 도메인 모델이 아니다.
 *
 * storage/jpa/build.gradle의 jpaDsl 선언에 따라 스칼라 필드 매핑(column = 'display_name',
 * nullable = true), 열거형 매핑(enumStrategy = 'STRING'), 그리고 값 객체 컨버터 매핑(column = 'code_value',
 * storageType = 'String', converter = 'cc.midolog.storage.jpa.sample.ScalarSampleCodeJpaConverter')이
 * 코드 생성기를 통해 올바르게 변환되는지 검증한다.
 */
data class ScalarSample(
    val id: String,
    val displayName: String,
    val nickname: String?,
    val status: ScalarSampleStatus,
    val code: ScalarSampleCode,
)

/**
 * JPA DSL 생성기 검증용 열거형 픽스처이며 도메인 모델이 아니다.
 *
 * storage/jpa/build.gradle의 enumStrategy = 'STRING' 매핑을 검증한다.
 */
enum class ScalarSampleStatus {
    ACTIVE,
    DISABLED,
}

/**
 * JPA DSL 생성기 검증용 값 객체 픽스처이며 도메인 모델이 아니다.
 *
 * storage/jpa/build.gradle의 converter 선언을 통한 스칼라 변환을 검증한다.
 */
@JvmInline
value class ScalarSampleCode(val value: String)
