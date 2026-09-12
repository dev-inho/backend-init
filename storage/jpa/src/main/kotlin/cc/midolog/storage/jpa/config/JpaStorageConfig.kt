package cc.midolog.storage.jpa.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate

/**
 * JPA 영속성 계층 설정 클래스.
 *
 * jpa 프로파일(@Profile("jpa"))에서만 활성화되어 mybatis 등 다른 영속성 구현체와의 빈 충돌을 방지한다.
 * @EntityScan과 @EnableJpaRepositories의 대상을 자기 패키지("cc.midolog.storage.jpa")로 한정하여
 * 타 모듈의 불필요한 스캔을 막고, JPA DSL 플러그인이 생성한 엔티티와 리포지토리만 등록되도록 격리한다.
 * 이 모듈의 src/main/kotlin에는 수기 작성한 어댑터와 설정 코드만 두고, DSL로 자동 생성된
 * 엔티티·매퍼·리포지토리는 Gradle build 디렉터리 아래에만 위치시키는 구조적 경계를 유지한다(README 참조).
 */
@Profile("jpa")
@Configuration
@EntityScan("cc.midolog.storage.jpa")
@EnableJpaRepositories("cc.midolog.storage.jpa")
class JpaStorageConfig {

    @Bean
    fun jpaTransactionOperations(
        transactionManager: PlatformTransactionManager,
    ): TransactionOperations = TransactionTemplate(transactionManager)
}
