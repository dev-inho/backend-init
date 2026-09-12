package cc.midolog.jpadsl.fixture

/**
 * JPA DSL 생성기 검증용 픽스처이며 도메인 모델이 아니다.
 *
 * storage/jpa/build.gradle의 1:N 관계 선언(relation('children') { type = 'oneToMany',
 * target = 'cc.midolog.jpadsl.fixture.RelationChild', mappedBy = 'parent', toDomain = 'emptyList()' })을 통해
 * 지연 로딩 컬렉션 생성과 도메인 변환 시 무한 재귀 및 프록시 접근을 방지하는 toDomain 정책을 검증한다.
 */
data class RelationParent(
    val id: String,
    val name: String,
    val children: List<RelationChild> = emptyList(),
)
