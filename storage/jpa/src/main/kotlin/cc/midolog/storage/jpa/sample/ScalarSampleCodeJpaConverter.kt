package cc.midolog.storage.jpa.sample

import cc.midolog.jpadsl.fixture.ScalarSampleCode

/**
 * JPA DSL 생성기 검증 픽스처용 컨버터.
 *
 * storage/jpa/build.gradle:78의 DSL 선언(converter = 'cc.midolog.storage.jpa.sample.ScalarSampleCodeJpaConverter')에
 * 연결되어 도메인 값 객체 ScalarSampleCode와 단일 스칼라 문자열(String) 간의 상호 변환을 수행한다.
 * 생성된 JPA 매퍼 및 엔티티가 toStorage와 toDomain 함수를 호출해 값 객체를 영속화하고 복원한다.
 */
object ScalarSampleCodeJpaConverter {
    fun toStorage(code: ScalarSampleCode): String = code.value

    fun toDomain(value: String): ScalarSampleCode = ScalarSampleCode(value)
}
