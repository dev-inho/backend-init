package cc.midolog.storage.mybatis.config

import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * MyBatis 영속성 저장소 설정 클래스.
 *
 * mybatis 프로파일(@Profile("mybatis"))에서만 활성화되어 JPA 등 다른 저장소 구현체와의
 * 빈 충돌을 방지한다.
 * @MapperScan의 basePackages에 매퍼 인터페이스 위치를 명시적으로 열거하여 불필요한 클래스
 * 스캔을 방지하고 의도된 매퍼만 빈으로 등록한다. 새로운 바운디드 컨텍스트나 도메인 매퍼를 추가할 때는
 * basePackages 배열에 해당 패키지를 추가해야 한다.
 * 등록 대상 패키지의 누락 여부는 MyBatisStorageConfigTest 가드로 검증한다.
 */
@Profile("mybatis")
@Configuration
@MapperScan(
    basePackages = [
        "cc.midolog.storage.mybatis.sample",
        "cc.midolog.storage.mybatis.user",
        "cc.midolog.storage.mybatis.file",
    ],
)
class MyBatisStorageConfig
