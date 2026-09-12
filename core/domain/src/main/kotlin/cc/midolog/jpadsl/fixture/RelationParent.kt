package cc.midolog.jpadsl.fixture

/**
 * JPA DSL 생성기 검증용 픽스처.
 * 
 * 도메인 모델이 아니다.
 */
data class RelationParent(
    val id: String,
    val name: String,
    val children: List<RelationChild> = emptyList(),
)
