package cc.midolog.jpadsl.fixture

/**
 * JPA DSL 생성기 검증용 픽스처.
 * 
 * 도메인 모델이 아니다.
 */
data class RelationChild(
    val id: String,
    val parentId: String,
    val name: String,
)
