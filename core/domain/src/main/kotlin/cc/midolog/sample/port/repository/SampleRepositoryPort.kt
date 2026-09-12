package cc.midolog.sample.port.repository

import cc.midolog.sample.model.Sample

/**
 * 샘플 도메인 영속성을 위한 도메인 저장소 포트.
 *
 * 구현체는 런타임 활성 프로파일에 따라 바인딩된다:
 * - mybatis 프로파일: MyBatisSampleRepositoryAdapter
 * - jpa 프로파일: JpaSampleRepositoryAdapter
 *
 * 하부 영속 계층은 블로킹 I/O를 수행하므로, 어댑터 구현체는 리액티브 이벤트루프가
 * 차단되지 않도록 모든 호출을 Dispatchers.IO 문맥으로 격리해야 한다.
 */
interface SampleRepositoryPort {
    /**
     * 식별자로 샘플을 단건 조회한다.
     * 주어진 식별자에 해당하는 샘플 행이 존재하지 않으면 null을 반환한다.
     */
    suspend fun findById(id: String): Sample?

    /**
     * 샘플을 저장한다.
     * 동일 식별자가 없으면 삽입하고 이미 존재하면 기존 행을 덮어쓰는 upsert 동작을 보장한다.
     */
    suspend fun save(sample: Sample): Sample
}
