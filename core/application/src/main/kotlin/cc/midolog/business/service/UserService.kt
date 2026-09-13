package cc.midolog.business.service

import cc.midolog.user.model.User
import cc.midolog.user.policy.UserSavePolicy
import cc.midolog.user.port.repository.UserRepositoryPort
import org.springframework.stereotype.Service

/**
 * 사용자 도메인 엔티티의 조회 및 저장을 담당하는 비즈니스 서비스.
 *
 * 특정 스토리지 기술(JPA, MyBatis 등)에 직접 결합되지 않도록 도메인 출력 포트인 [UserRepositoryPort]에만 의존한다.
 * 실제 스토리지 어댑터는 런타임 의존성(runtimeOnly)으로 격리되어 주입되며,
 * 활성화된 영속성 프로파일(jpa 또는 mybatis)에 따라 적절한 어댑터가 단일 빈으로 등록되는지는 [cc.midolog.persistence.PersistenceProfileContextTest]가 검증한다.
 */
@Service
class UserService(
    private val userRepositoryPort: UserRepositoryPort,
    private val userSavePolicies: List<UserSavePolicy> = emptyList(),
) {
    /**
     * 식별자로 사용자를 조회하며 대상이 없으면 null을 반환한다.
     */
    suspend fun findById(id: String): User? =
        userRepositoryPort.findById(id)

    /**
     * 사용자 정보를 등록된 [UserSavePolicy] 정책들의 [UserSavePolicy.order] 오름차순으로 적용한 후
     * 영속 저장소에 저장하거나 기존 정보를 갱신(upsert)한다.
     *
     * 영속성 계층(MyBatis 매퍼의 ON CONFLICT DO UPDATE 구문 또는 JPA save 동작)의 upsert 규약에 따라,
     * 동일 id를 가진 사용자가 이미 존재하면 전달된 데이터로 덮어쓰고 신규 id이면 새 레코드로 삽입한다.
     */
    suspend fun save(user: User): User =
        userRepositoryPort.save(
            userSavePolicies
                .sortedBy { it.order }
                .fold(user) { acc, policy -> policy.beforeSave(acc) },
        )
}
