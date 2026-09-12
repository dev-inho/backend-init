package cc.midolog.jpadsl.fixture

/**
 * JPA DSL 생성기 검증용 픽스처이며 도메인 모델이 아니다.
 *
 * storage/jpa/build.gradle의 N:1 외래 키 관계 선언(field('parentId') { relation = 'parent' },
 * relation('parent') { type = 'manyToOne', target = 'cc.midolog.jpadsl.fixture.RelationParent',
 * sourceField = 'parentId', joinColumn = 'parent_id', referencedColumn = 'id' })을 통해
 * 스칼라 식별자 필드가 연관 엔티티 참조로 올바르게 치환 및 생성되는지 검증한다.
 */
data class RelationChild(
    val id: String,
    val parentId: String,
    val name: String,
)
