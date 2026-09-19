package cc.midolog.storage.jpa.customer

import cc.midolog.storage.jpa.customers.acme.AcmeOrderNoteJpaAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Import

/**
 * 고객 확장 엔티티의 JPA 영속성 어댑터를 등록하는 자동 구성 클래스.
 *
 * storage.persistence.provider=jpa 프로퍼티가 활성화된 경우에만 로드되며,
 * 생성된 고객별 JPA 자동 구성 설정을 스프링 컨텍스트에 등록한다.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "storage.persistence", name = ["provider"], havingValue = "jpa")
@Import(AcmeOrderNoteJpaAutoConfiguration::class)
class JpaCustomerAutoConfiguration
