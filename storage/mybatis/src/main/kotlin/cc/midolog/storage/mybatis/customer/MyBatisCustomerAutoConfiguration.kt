package cc.midolog.storage.mybatis.customer

import cc.midolog.storage.mybatis.customers.acme.AcmeOrderNoteMyBatisAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Import

/**
 * 고객 확장 엔티티를 위한 MyBatis 저장소 자동 구성 통합 클래스.
 *
 * storage.persistence.provider=mybatis 프로퍼티가 활성화된 경우에만 동작하며,
 * 생성된 고객별 MyBatis 자동 구성 설정을 스프링 컨텍스트에 등록한다.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "storage.persistence", name = ["provider"], havingValue = "mybatis")
@Import(AcmeOrderNoteMyBatisAutoConfiguration::class)
class MyBatisCustomerAutoConfiguration
