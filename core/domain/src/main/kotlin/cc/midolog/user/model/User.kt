package cc.midolog.user.model

/**
 * 사용자 도메인 모델.
 *
 * 불변(data class)으로 정의하여 식별자와 프로퍼티의 일관성을 보장한다.
 * 프레임워크 비의존 모듈이라 영속성 어노테이션을 쓰지 않으며,
 * 영속성 계층과의 매핑은 storage 모듈(JPA DSL 또는 MyBatis 매퍼)에서 담당한다.
 * 이 모듈의 순수성은 DomainPurityTest가 검증한다.
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
)
