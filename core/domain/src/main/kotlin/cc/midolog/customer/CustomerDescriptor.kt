package cc.midolog.customer

/**
 * 런타임에 활성화된 고객 식별 메타데이터를 나타내는 도메인 기술자.
 *
 * 프레임워크 비의존 순수 Kotlin 데이터 객체이며, 기동 시 애플리케이션 프로퍼티(app.customer)와 일치 여부를 검증하는 데 사용된다.
 */
data class CustomerDescriptor(
    val name: String,
)
