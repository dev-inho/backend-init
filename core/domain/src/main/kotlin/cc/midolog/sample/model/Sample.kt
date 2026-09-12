package cc.midolog.sample.model

/**
 * 신규 도메인 구현의 기준이 되는 샘플 도메인 모델.
 *
 * 불변(data class)으로 정의하여 값의 불변성과 동등성 비교를 보장한다.
 * 프레임워크 비의존 모듈이라 영속성 어노테이션을 쓰지 않으며,
 * 영속성 매핑은 storage 모듈(JPA DSL 또는 MyBatis 매퍼)에서 수행한다.
 * 도메인 모델의 순수성은 DomainPurityTest가 검증한다.
 * 실제 비즈니스 도메인을 추가할 때도 이 패턴(model/port)을 따른다.
 */
data class Sample(
    val id: String,
    val name: String,
)
