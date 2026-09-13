package cc.midolog.sample.policy

import cc.midolog.sample.model.Sample

/**
 * 샘플 엔티티가 영속 저장소에 저장되기 직전에 부가 비즈니스 규칙을 적용하기 위한 도메인 확장 정책 인터페이스.
 *
 * 프레임워크나 인프라 라이브러리에 결합되지 않는 순수 Kotlin 계약이며, 복수의 정책이 등록된 경우 [order] 오름차순으로 순차 적용된다.
 */
interface SampleSavePolicy {
    /** 정책 적용 우선순위 순서 번호이며 기본값은 0이다. */
    val order: Int get() = 0

    /**
     * 샘플 저장 직전에 호출되어 엔티티를 변환하거나 유효성을 검증하고 변환된 새 인스턴스를 반환한다.
     */
    suspend fun beforeSave(sample: Sample): Sample
}