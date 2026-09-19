package cc.midolog.customer

/**
 * [CustomerDescriptor] 빈 정의에 고객 식별 메타데이터를 선언하는 어노테이션.
 *
 * 프레임워크가 [CustomerDescriptor] 인스턴스를 조기 생성(early instantiation)하지 않고도
 * 빈 정의 메타데이터 단계에서 활성화된 고객 식별자를 안전하게 추출하여 검증할 수 있도록 지원한다.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class CustomerDescriptorMetadata(
    val name: String,
)
