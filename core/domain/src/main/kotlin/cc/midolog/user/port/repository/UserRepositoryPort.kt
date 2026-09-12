package cc.midolog.user.port.repository

import cc.midolog.user.model.User

/**
 * 사용자 영속성을 위한 도메인 저장소 포트.
 *
 * 구현체는 런타임 활성 프로파일에 따라 바인딩된다:
 * - mybatis 프로파일: MyBatisUserRepositoryAdapter
 * - jpa 프로파일: JpaUserRepositoryAdapter
 *
 * 하부 저장소는 블로킹 I/O를 사용하므로, 어댑터 구현체는 WebFlux 이벤트루프 스레드를
 * 차단하지 않도록 모든 호출을 Dispatchers.IO 문맥에서 실행해야 한다.
 */
interface UserRepositoryPort {
    /**
     * 식별자로 사용자를 단건 조회한다.
     * 주어진 식별자에 해당하는 데이터가 없으면 null을 반환한다.
     */
    suspend fun findById(id: String): User?

    /**
     * 사용자를 저장한다.
     * 동일 식별자가 없으면 삽입하고 이미 존재하면 갱신하는 upsert 동작을 보장한다.
     */
    suspend fun save(user: User): User
}
